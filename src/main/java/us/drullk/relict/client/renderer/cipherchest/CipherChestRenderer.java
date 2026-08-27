package us.drullk.relict.client.renderer.cipherchest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState.ChestMaterialType;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.block.cipherchest.CipherChestBlock;
import us.drullk.relict.block.cipherchest.CipherChestBlockEntity;
import us.drullk.relict.block.cipherchest.CipherChestFaceLayout;
import us.drullk.relict.block.cipherchest.CipherChestSquare;
import net.minecraft.world.phys.AABB;

/**
 * [VANILLACOPY] net.minecraft.client.renderer.blockentity.ChestRenderer (26.2.0.64), trimmed to
 * single-chest-only (no {@code MultiblockChestResources}, no {@code combine()}, no {@code ChestType} state
 * lookup -- always {@code ChestType.SINGLE}) and the material forced to {@code CHRISTMAS} unconditionally.
 * Vanilla's own renderer only shows that skin during the real December date window
 * ({@code SpecialDates.isExtendedChristmas()}), which isn't what "placeholder texture" means for this
 * order, so that check is dropped rather than copied. {@link ChestRenderer#modelTransformation} itself is
 * called directly (not copied) since it's public.
 */
public class CipherChestRenderer implements BlockEntityRenderer<CipherChestBlockEntity, CipherChestRenderState> {

    private static final float GLYPH_SCALE = 0.01F;
    private static final float FACE_EPSILON = 1.0F / 256.0F;
    private static final int BLANK_QUAD_COLOR = 0xFF1A1410;
    private static final float BLANK_QUAD_HALF_SIZE = 0.06F;
    private static final int TEXT_COLOR = 0xFFE8DCC8;

    // Sine-wave red blink tunables (order-mandated named constants). Period is short enough to read as an
    // urgent "wrong" pulse; duration is tied 1:1 to CipherChestBlockEntity.LOCKOUT_TICKS so the blink can
    // never outlive the lockout it's juicing. The final BLINK_TAPER_TICKS of that duration fade the sine
    // envelope down to zero instead of cutting off abruptly at the lockout's last tick.
    private static final float BLINK_PERIOD_TICKS = 6.0F;
    private static final float BLINK_TAPER_TICKS = 20.0F;
    private static final int BLINK_COLOR = 0xFFFF2A2A;

    private final SpriteGetter sprites;
    private final ChestModel model;
    private final Font font;

    public CipherChestRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
        this.model = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
        this.font = context.font();
    }

    @Override
    public CipherChestRenderState createRenderState() {
        return new CipherChestRenderState();
    }

    @Override
    public void extractRenderState(CipherChestBlockEntity blockEntity, CipherChestRenderState state, float partialTicks,
            Vec3 cameraPosition, @Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        BlockState blockState = blockEntity.getBlockState();
        state.facing = blockState.getValue(CipherChestBlock.FACING);
        state.open = blockEntity.getOpenNess(partialTicks);
        state.solved = blockEntity.isSolved();

        long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0L;
        state.blinking = blockEntity.isLockedOut(gameTime);
        state.blinkPhase = gameTime + partialTicks;
        state.lockoutUntil = blockEntity.lockoutUntilGameTime();

        for (int cell = 0; cell < CipherChestSquare.CELL_COUNT; cell++) {
            state.values[cell] = blockEntity.displayValueAt(cell);
            state.blank[cell] = blockEntity.isBlank(cell);
        }
    }

    @Override
    public void submit(CipherChestRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));
        float openness = 1.0F - state.open;
        openness = 1.0F - openness * openness * openness;
        SpriteId spriteId = Sheets.chooseSprite(ChestMaterialType.CHRISTMAS, ChestType.SINGLE);
        submitNodeCollector.submitModel(this.model, openness, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, spriteId, this.sprites, 0, state.breakProgress);
        poseStack.popPose();

        if (!state.solved) {
            float blinkIntensity = blinkIntensity(state);
            submitGrid(state, poseStack, submitNodeCollector, blinkIntensity);
            submitLatchBlink(state, poseStack, submitNodeCollector, blinkIntensity);
        }
    }

    private float blinkIntensity(CipherChestRenderState state) {
        if (!state.blinking) {
            return 0.0F;
        }
        double phase = 2.0 * Math.PI * (state.blinkPhase / BLINK_PERIOD_TICKS);
        float sine = (float) ((Math.sin(phase) + 1.0) * 0.5);
        return sine * fadeEnvelope(state);
    }

    /**
     * Full amplitude (1.0) until the lockout's final {@link #BLINK_TAPER_TICKS}, then a linear ramp down to
     * invisible (0.0) exactly as the lockout ends -- so the blink tapers out instead of cutting off on the
     * lockout's last tick.
     */
    private float fadeEnvelope(CipherChestRenderState state) {
        float remainingTicks = state.lockoutUntil - state.blinkPhase;
        if (remainingTicks >= BLINK_TAPER_TICKS) {
            return 1.0F;
        }
        return Math.max(0.0F, remainingTicks / BLINK_TAPER_TICKS);
    }

    private void submitGrid(CipherChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector, float blinkIntensity) {
        for (int cell = 0; cell < CipherChestSquare.CELL_COUNT; cell++) {
            int row = CipherChestSquare.rowOf(cell);
            int col = CipherChestSquare.colOf(cell);
            double[] local = CipherChestFaceLayout.localFromAcrossAlong(state.facing,
                    CipherChestFaceLayout.acrossCenterOf(col), CipherChestFaceLayout.alongCenterOf(row));

            if (state.blank[cell]) {
                int color = state.blinking ? ARGB.srgbLerp(blinkIntensity, BLANK_QUAD_COLOR, BLINK_COLOR) : BLANK_QUAD_COLOR;
                submitBlankQuad(poseStack, collector, local[0], local[1], color);
            }
            submitGlyph(poseStack, collector, state.facing, state.lightCoords, local[0], local[1], state.values[cell]);
        }
    }

    private void submitBlankQuad(PoseStack poseStack, SubmitNodeCollector collector, double localX, double localZ, int color) {
        poseStack.pushPose();
        poseStack.translate(localX, CipherChestFaceLayout.LID_TOP_Y + FACE_EPSILON, localZ);
        float h = BLANK_QUAD_HALF_SIZE;
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            buffer.addVertex(pose, -h, 0.0F, -h).setColor(color);
            buffer.addVertex(pose, -h, 0.0F, h).setColor(color);
            buffer.addVertex(pose, h, 0.0F, h).setColor(color);
            buffer.addVertex(pose, h, 0.0F, -h).setColor(color);
        });
        poseStack.popPose();
    }

    /**
     * Lays the glyph flat on the lid (rotated out of its default vertical drawing plane) and yaws it by the
     * same facing rotation the door's panel used, so it reads right-side-up to a viewer standing on the
     * latch side. The lid is a new (horizontal) face the door never rendered on, so this specific rotation
     * is new math, not a direct carry-over -- flagged for the eyes-on gate same as the door's own facing
     * sign was, before it was confirmed correct.
     */
    private void submitGlyph(PoseStack poseStack, SubmitNodeCollector collector, Direction facing, int lightCoords, double localX, double localZ, int value) {
        String text = Integer.toString(value);
        int pixelWidth = this.font.width(text);

        poseStack.pushPose();
        poseStack.translate(localX, CipherChestFaceLayout.LID_TOP_Y + FACE_EPSILON * 2, localZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(CipherChestFaceLayout.yRotationDegreesFor(facing)));
        poseStack.scale(-GLYPH_SCALE, -GLYPH_SCALE, -GLYPH_SCALE);
        float x = -pixelWidth / 2.0F;
        float y = -this.font.lineHeight / 2.0F;
        collector.order(1).submitText(poseStack, x, y, Component.literal(text).getVisualOrderText(), false, DisplayMode.NORMAL, lightCoords, TEXT_COLOR, 0, 0);
        poseStack.popPose();
    }

    /**
     * The latch itself is baked into the shared vanilla chest model, so it can't be tinted in isolation --
     * this draws a translucent red decal quad coincident with the latch hover box instead (a smaller
     * mechanism than re-baking or re-coloring part of a shared model). Only rendered while blinking.
     */
    private void submitLatchBlink(CipherChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector, float blinkIntensity) {
        if (!state.blinking) {
            return;
        }
        AABB box = CipherChestFaceLayout.latchHoverShape(state.facing).bounds();
        int color = ARGB.color((int) (blinkIntensity * 200), 255, 20, 20);

        poseStack.pushPose();
        Direction.Axis axis = state.facing.getAxis();
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            if (axis == Direction.Axis.Z) {
                float y0 = (float) box.minY, y1 = (float) box.maxY, x0 = (float) box.minX, x1 = (float) box.maxX;
                float z = state.facing == Direction.NORTH ? (float) box.minZ - FACE_EPSILON : (float) box.maxZ + FACE_EPSILON;
                buffer.addVertex(pose, x0, y0, z).setColor(color);
                buffer.addVertex(pose, x1, y0, z).setColor(color);
                buffer.addVertex(pose, x1, y1, z).setColor(color);
                buffer.addVertex(pose, x0, y1, z).setColor(color);
            } else {
                float y0 = (float) box.minY, y1 = (float) box.maxY, z0 = (float) box.minZ, z1 = (float) box.maxZ;
                float x = state.facing == Direction.WEST ? (float) box.minX - FACE_EPSILON : (float) box.maxX + FACE_EPSILON;
                buffer.addVertex(pose, x, y0, z0).setColor(color);
                buffer.addVertex(pose, x, y0, z1).setColor(color);
                buffer.addVertex(pose, x, y1, z1).setColor(color);
                buffer.addVertex(pose, x, y1, z0).setColor(color);
            }
        });
        poseStack.popPose();
    }

}
