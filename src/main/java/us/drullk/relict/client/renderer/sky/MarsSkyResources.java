package us.drullk.relict.client.renderer.sky;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Mars sky's own GPU buffers, built once against the celestials atlas and thrown away whenever it is
 * rebuilt.
 * <p>
 * Vanilla's {@code SkyRenderer} keeps its sun, moon, and star buffers private and only exposes them through
 * {@code renderSunMoonAndStars}, which draws all three together — there is no way to borrow its stars alone
 * while substituting the rest. Its sky disc and sunrise fan <em>are</em> reachable, and those the renderer
 * calls directly rather than duplicating.
 */
public class MarsSkyResources implements AutoCloseable {

    private static final Identifier SUN_SPRITE = Identifier.withDefaultNamespace("sun");
    private static final float CELESTIAL_DISTANCE = 100.0F;

    /**
     * Denser than vanilla's 1500. Mars has a hundredth of Earth's air and no water vapour to scatter in it,
     * so its night sky genuinely shows far more stars; the seed differs from vanilla's so the constellations
     * are not the Overworld's rearranged.
     */
    private static final int STAR_COUNT = 2500;
    private static final long STAR_SEED = 41971L;

    private final TextureAtlas celestials;
    private final GpuBuffer starBuffer;
    private final int starIndexCount;
    private final GpuBuffer sunBuffer;
    private final Map<MarsMoons, GpuBuffer> moonBuffers = new EnumMap<>(MarsMoons.class);

    public MarsSkyResources(TextureAtlas celestials) {
        this.celestials = celestials;
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;

        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
            addQuad(builder, celestials.getSprite(SUN_SPRITE));

            try (MeshData mesh = builder.buildOrThrow()) {
                this.sunBuffer = RenderSystem.getDevice().createBuffer(() -> "Mars sun quad", 32, mesh.vertexBuffer());
            }
        }

        for (MarsMoons moon : MarsMoons.values()) {
            this.moonBuffers.put(moon, buildMoon(celestials, moon, format));
        }

        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(STAR_COUNT * 4 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            addStars(builder);

            try (MeshData mesh = builder.buildOrThrow()) {
                this.starIndexCount = mesh.drawState().indexCount();
                this.starBuffer = RenderSystem.getDevice().createBuffer(() -> "Mars stars", 40, mesh.vertexBuffer());
            }
        }
    }

    /**
     * One quad per phase followed by the occlusion mask, so a moon is a single buffer and the two draws
     * differ only in base vertex. Sharing the buffer also guarantees the mask and the lit sprite are wound
     * identically, which is what keeps the blot registered with the body it belongs to.
     */
    private static GpuBuffer buildMoon(TextureAtlas celestials, MarsMoons moon, VertexFormat format) {
        int quads = moon.phases() + 1;

        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(quads * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);

            for (Identifier phase : moon.phaseSprites()) {
                addQuad(builder, celestials.getSprite(phase));
            }

            addQuad(builder, celestials.getSprite(moon.occlusionSprite()));

            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Mars moon " + moon.name(), 32, mesh.vertexBuffer());
            }
        }
    }

    /**
     * A unit quad in the horizontal plane, matching how vanilla builds its sun. Vanilla mirrors its moon's
     * texture coordinates relative to this; the Mars moons are not mirrored, so the sprites appear the way
     * the rasterizer drew them.
     */
    private static void addQuad(BufferBuilder builder, TextureAtlasSprite sprite) {
        builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV0());
        builder.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV0());
        builder.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV1());
        builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV1());
    }

    private static void addStars(BufferBuilder builder) {
        RandomSource random = RandomSource.createThreadLocalInstance(STAR_SEED);

        for (int star = 0; star < STAR_COUNT; star++) {
            float x = random.nextFloat() * 2.0F - 1.0F;
            float y = random.nextFloat() * 2.0F - 1.0F;
            float z = random.nextFloat() * 2.0F - 1.0F;
            float size = 0.15F + random.nextFloat() * 0.1F;
            float lengthSquared = Mth.lengthSquared(x, y, z);
            // Rejecting the corners of the cube keeps the distribution spherical rather than boxy.
            if (lengthSquared <= 0.010000001F || lengthSquared >= 1.0F) {
                continue;
            }

            Vector3f centre = new Vector3f(x, y, z).normalize(CELESTIAL_DISTANCE);
            float spin = (float) (random.nextDouble() * (float) Math.PI * 2.0);
            Matrix3f rotation = new Matrix3f()
                    .rotateTowards(new Vector3f(centre).negate(), new Vector3f(0.0F, 1.0F, 0.0F))
                    .rotateZ(-spin);
            builder.addVertex(new Vector3f(size, -size, 0.0F).mul(rotation).add(centre));
            builder.addVertex(new Vector3f(size, size, 0.0F).mul(rotation).add(centre));
            builder.addVertex(new Vector3f(-size, size, 0.0F).mul(rotation).add(centre));
            builder.addVertex(new Vector3f(-size, -size, 0.0F).mul(rotation).add(centre));
        }
    }

    /** Held rather than looked up per draw; it is discarded with the buffers whenever it is rebuilt. */
    public TextureAtlas celestials() {
        return this.celestials;
    }

    public GpuBuffer starBuffer() {
        return this.starBuffer;
    }

    public int starIndexCount() {
        return this.starIndexCount;
    }

    public GpuBuffer sunBuffer() {
        return this.sunBuffer;
    }

    public GpuBuffer moonBuffer(MarsMoons moon) {
        return this.moonBuffers.get(moon);
    }

    @Override
    public void close() {
        this.starBuffer.close();
        this.sunBuffer.close();
        this.moonBuffers.values().forEach(GpuBuffer::close);
    }

}
