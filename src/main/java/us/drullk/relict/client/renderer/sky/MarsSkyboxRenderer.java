package us.drullk.relict.client.renderer.sky;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.Relict;
import us.drullk.relict.client.renderer.RelictRenderPipelines;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Mars's sky: a smaller sun, a denser star field, and Phobos and Deimos on arcs of their own.
 * <p>
 * {@code renderSky} returning true suppresses every part of the vanilla sky at once, so the sky disc and
 * the void disc are drawn here too even though neither is Mars-specific — they are the cost of the hook,
 * not a feature. Both are borrowed from vanilla's renderer rather than rebuilt. The sunrise fan is called
 * for the same reason; Mars's timeline sets no {@code sunrise_sunset_color} yet, so it early-returns on a
 * transparent colour and is waiting for the blue twilight to be written.
 * <p>
 * <strong>The bodies are not drawn onto the sky.</strong> They go into a black offscreen layer of their own
 * — stars, sun, then each moon — which is then composited additively over the sky. Within that layer a moon
 * first erases its own outline and then adds its lit face, so it reads as a solid object: stars stop
 * shining through it, and a moon crossing the sun genuinely subtracts the sun's light rather than merely
 * failing to add its own.
 * <p>
 * The layer is what makes the erase safe. Erasing straight onto the sky would leave black holes, and
 * tinting the erase with the sky colour would have to account for fog as well and would still drift near
 * the horizon. Against a layer that starts black, erased means transparent, and the sky is never touched.
 * Vanilla uses the same arrangement for entity outlines.
 */
public class MarsSkyboxRenderer implements CustomSkyboxRenderer {

    public static final MarsSkyboxRenderer INSTANCE = new MarsSkyboxRenderer();

    private static final ContextKey<MarsSkyState> SKY_STATE = new ContextKey<>(Relict.id("mars_sky"));

    private static final float CELESTIAL_HEIGHT = 100.0F;
    private static final Vector4f LAYER_CLEAR = new Vector4f(0.0F);

    private @Nullable MarsSkyResources resources;

    /**
     * Sized to the main target and resized with it, so it lives for the session rather than per reload —
     * a different lifetime from {@link #resources}, which follows the celestials atlas.
     */
    private @Nullable RenderTarget celestialTarget;

    private MarsSkyboxRenderer() {
    }

    /**
     * Sky placement per moon, indexed by {@link MarsMoons#ordinal()}.
     * <p>
     * A record so the storm parameters can join it later without rethreading anything: note 12 derives
     * audio from a shared {@code tau}, and note 09 wants the same value darkening this sky.
     */
    public record MarsSkyState(float[] angles, float[] inclinations, float[] scales) {

        public float angle(MarsMoons moon) {
            return this.angles[moon.ordinal()];
        }

        public float inclination(MarsMoons moon) {
            return this.inclinations[moon.ordinal()];
        }

        public float scale(MarsMoons moon) {
            return this.scales[moon.ordinal()];
        }

    }

    /**
     * NeoForge resolves the level's custom skybox immediately before firing this event, so comparing against
     * it is an exact test for "the camera is somewhere this renderer is responsible for" — better than
     * checking the dimension key, which would miss a datapack pointing another level at this sky.
     */
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        LevelRenderState renderState = event.getRenderState();
        if (renderState.customSkyboxRenderer != INSTANCE) {
            return;
        }

        MarsMoons[] moons = MarsMoons.values();
        float[] angles = new float[moons.length];
        float[] inclinations = new float[moons.length];
        float[] scales = new float[moons.length];
        EnvironmentAttributeProbe probe = event.getCamera().attributeProbe();
        float partialTicks = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (MarsMoons moon : moons) {
            angles[moon.ordinal()] = probe.getValue(moon.angle(), partialTicks);
            inclinations[moon.ordinal()] = probe.getValue(moon.inclination(), partialTicks);
            scales[moon.ordinal()] = probe.getValue(moon.scale(), partialTicks);
        }

        renderState.setRenderData(SKY_STATE, new MarsSkyState(angles, inclinations, scales));
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState, Matrix4fc modelViewMatrix, Runnable setupFog) {
        MarsSkyState state = levelRenderState.getRenderData(SKY_STATE);
        SkyRenderer vanilla = Minecraft.getInstance().levelRenderer.skyRenderer();
        if (state == null || vanilla == null) {
            // Nothing extracted for this frame; vanilla's sky is a better answer than an empty one.
            return false;
        }

        // The atlas is rebuilt on a resource reload, which is exactly when vanilla discards its own sky
        // buffers, so the flag it already carries is the right moment to discard ours.
        if (levelRenderState.shouldResetSkyRenderer) {
            this.closeResources();
        }

        if (this.resources == null) {
            TextureAtlas celestials = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
            this.resources = new MarsSkyResources(celestials);
        }

        MarsSkyResources resources = this.resources;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.width <= 0 || main.height <= 0) {
            return false;
        }

        RenderTarget layer = this.celestialLayer(main);
        setupFog.run();
        vanilla.renderSkyDisc(skyRenderState.skyColor);

        PoseStack poseStack = new PoseStack();
        vanilla.renderSunriseAndSunset(poseStack, skyRenderState.sunAngle, skyRenderState.sunriseAndSunsetColor);

        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(layer.getColorTexture(), LAYER_CLEAR);
        GpuTextureView layerView = layer.getColorTextureView();

        float sunAngleDegrees = (float) Math.toDegrees(skyRenderState.sunAngle);

        if (skyRenderState.starBrightness > 0.0F) {
            orient(poseStack, 0.0F, (float) Math.toDegrees(skyRenderState.starAngle));
            drawStars(layerView, resources, skyRenderState.starBrightness, poseStack);
            poseStack.popPose();
        }

        orient(poseStack, 0.0F, sunAngleDegrees);
        drawQuad(layerView, "Mars sun", RenderPipelines.CELESTIAL, resources.celestials(), resources.sunBuffer(), 0,
                poseStack, RelictDimension.SUN_QUAD_EXTENT, new Vector4f(1.0F, 1.0F, 1.0F, skyRenderState.rainBrightness));
        poseStack.popPose();

        for (MarsMoons moon : MarsMoons.values()) {
            drawMoon(layerView, resources, moon, state, sunAngleDegrees, skyRenderState.rainBrightness, poseStack);
        }

        composite(layer, main);

        if (skyRenderState.shouldRenderDarkDisc) {
            vanilla.renderDarkDisc();
        }

        return true;
    }

    private RenderTarget celestialLayer(RenderTarget main) {
        if (this.celestialTarget == null) {
            // No depth: not one sky pipeline declares a DepthStencilState, so nothing here depth-tests.
            this.celestialTarget = new TextureTarget("Mars celestials", main.width, main.height, false, GpuFormat.RGBA8_UNORM);
        } else if (this.celestialTarget.width != main.width || this.celestialTarget.height != main.height) {
            this.celestialTarget.resize(main.width, main.height);
        }

        return this.celestialTarget;
    }

    private static void composite(RenderTarget layer, RenderTarget main) {
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Mars celestial composite", main.getColorTextureView(), Optional.empty(),
                        main.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RelictRenderPipelines.CELESTIAL_COMPOSITE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", layer.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            // core/screenquad derives a fullscreen triangle from gl_VertexID, so there is no vertex buffer.
            pass.draw(3, 1, 0, 0);
        }
    }

    /**
     * Points the stack at a body: rolls its orbital plane, then swings along the arc.
     * <p>
     * The roll is folded into the {@code -90} vanilla applies before its own arc rotation. Because that outer
     * rotation is about the world Y axis, rolling it leaves every plane containing the vertical — so every
     * body still climbs through the zenith, and the planes all intersect there. That shared axis is what
     * makes conjunctions, and therefore eclipses, possible at all.
     * <p>
     * Leaves one pose pushed for the caller to pop.
     */
    private static void orient(PoseStack poseStack, float inclinationDegrees, float angleDegrees) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F + inclinationDegrees));
        poseStack.mulPose(Axis.XP.rotationDegrees(angleDegrees));
    }

    private static void drawMoon(GpuTextureView layer, MarsSkyResources resources, MarsMoons moon, MarsSkyState state,
                                 float sunAngleDegrees, float rainBrightness, PoseStack poseStack) {
        float angle = state.angle(moon);
        float inclination = state.inclination(moon);
        float extent = moon.quadExtent() * state.scale(moon);

        orient(poseStack, inclination, angle);

        GpuBuffer buffer = resources.moonBuffer(moon);
        TextureAtlas celestials = resources.celestials();
        // The mask sits after the phases in the buffer. Erase first, back to the layer's black, then add the
        // lit face over the hole — so the unlit part of the moon ends up contributing nothing at all.
        drawQuad(layer, "Mars moon occluder", RelictRenderPipelines.CELESTIAL_OCCLUDER, celestials, buffer, moon.phases(),
                poseStack, extent, new Vector4f(0.0F, 0.0F, 0.0F, rainBrightness));
        drawQuad(layer, "Mars moon", RenderPipelines.CELESTIAL, celestials, buffer, phaseFrame(angle, sunAngleDegrees, inclination, moon.phases()),
                poseStack, extent, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness));

        poseStack.popPose();
    }

    /**
     * Which phase sprite a moon shows, from how far it sits from the sun in the sky.
     * <p>
     * The rasterizer generates frame {@code k} of {@code n} lit from {@code 360k/n} degrees off head-on, so
     * frame zero is full, frame {@code n/2} is new, and the mapping back is the elongation between moon and
     * sun.
     * <p>
     * Taken from the two direction vectors rather than a closed form in the angles, so it stays right through
     * whatever the geometry does next — and from the same {@code skyDirection} the transit solver uses, so the
     * two cannot disagree. The magnitude comes from the dot product; the sign, which is what separates waxing
     * from waning, comes from which side of the moon's orbital plane the sun falls on. {@code acos} alone
     * would fold the two halves of the cycle onto each other.
     */
    static int phaseFrame(float moonAngleDegrees, float sunAngleDegrees, float inclinationDegrees, int frames) {
        Vector3f moon = RelictDimension.skyDirection(inclinationDegrees, moonAngleDegrees);
        Vector3f sun = RelictDimension.skyDirection(0.0F, sunAngleDegrees);
        Vector3f normal = RelictDimension.orbitNormal(inclinationDegrees);
        double across = sun.cross(moon, new Vector3f()).dot(normal);
        double signed = Math.toDegrees(Math.atan2(across, moon.dot(sun)));
        return Math.floorMod(Math.round((float) ((180.0 - signed) * frames / 360.0)), frames);
    }

    private static void drawQuad(GpuTextureView layer, String name, RenderPipeline pipeline, TextureAtlas celestials, GpuBuffer buffer,
                                 int quad, PoseStack poseStack, float extent, Vector4f color) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, CELESTIAL_HEIGHT, 0.0F);
        modelViewStack.scale(extent, 1.0F, extent);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelViewStack), color);
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> name, layer, Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", celestials.getTextureView(), celestials.getSampler());
            pass.setVertexBuffer(0, buffer.slice());
            pass.setIndexBuffer(indices.getBuffer(6), indices.type());
            pass.drawIndexed(6, 1, 0, quad * 4, 0);
        }

        modelViewStack.popMatrix();
    }

    private static void drawStars(GpuTextureView layer, MarsSkyResources resources, float brightness, PoseStack poseStack) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        int indexCount = resources.starIndexCount();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(modelViewStack), new Vector4f(brightness, brightness, brightness, brightness));
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Mars stars", layer, Optional.empty())) {
            pass.setPipeline(RenderPipelines.STARS);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, resources.starBuffer().slice());
            pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }

        modelViewStack.popMatrix();
    }

    private void closeResources() {
        if (this.resources != null) {
            this.resources.close();
            this.resources = null;
        }
    }

}
