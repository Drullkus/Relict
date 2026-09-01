package us.drullk.relict.client.renderer.cipherchest;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;
import us.drullk.relict.block.cipherchest.CipherChestBlock;
import us.drullk.relict.block.cipherchest.CipherChestBlockEntity;
import us.drullk.relict.block.cipherchest.CipherChestFaceLayout;
import us.drullk.relict.init.RelictBlockEntities;
import us.drullk.relict.init.RelictBlocks;

/**
 * Registers the Cipher Chest's block-entity renderer and its locked-state hover-outline override.
 * <p>
 * <b>Hover mechanism:</b> NeoForge's
 * {@link ExtractBlockOutlineRenderStateEvent}, fired per-frame with the exact {@link BlockHitResult} the
 * crosshair is currently resolving. This is the smallest mechanism that can work at all: the alternative of
 * overriding {@code Block#getShape} cannot vary the outline by sub-block crosshair position, because the
 * {@code CollisionContext} it receives (built from {@code CollisionContext.of(player)}) carries no hit
 * location -- only this event's {@code BlockHitResult} does. No mixin is used; the event's
 * {@code addCustomRenderer} extension point is public NeoForge API for exactly this purpose. When a dial or
 * the latch is hit, one small custom outline is submitted via vanilla's own
 * {@code SubmitNodeCollector#submitShapeOutline} (the same primitive vanilla's default full-block outline
 * uses, just given a smaller shape) and vanilla's default outline is suppressed. When nothing special is
 * hit, no custom renderer is added at all, so vanilla draws its ordinary full-block outline unmodified --
 * the "whole chest" fallback needs zero extra code.
 */
public class RelictCipherChestRenderers {

    private static final int OUTLINE_COLOR = ARGB.black(102);
    private static final float OUTLINE_WIDTH = 2.0F;

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RelictCipherChestRenderers::onRegisterRenderers);
        NeoForge.EVENT_BUS.addListener(RelictCipherChestRenderers::onExtractBlockOutline);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RelictBlockEntities.CIPHER_CHEST.get(), CipherChestRenderer::new);
    }

    private static void onExtractBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        BlockState blockState = event.getBlockState();
        if (!blockState.is(RelictBlocks.CIPHER_CHEST.get())) {
            return;
        }

        BlockPos pos = event.getBlockPos();
        if (!(event.getLevel().getBlockEntity(pos) instanceof CipherChestBlockEntity chest) || chest.isSolved()) {
            return;
        }

        Direction facing = blockState.getValue(CipherChestBlock.FACING);
        BlockHitResult hit = event.getHitResult();
        Direction hitFace = hit.getDirection();
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

        VoxelShape hoverShape;
        if (CipherChestFaceLayout.isLatchHit(facing, hitFace, local.x, local.y, local.z)) {
            hoverShape = CipherChestFaceLayout.latchHoverShape(facing);
        } else {
            int cellIndex = CipherChestFaceLayout.cellIndexFromHit(facing, hitFace, local.x, local.z);
            if (cellIndex < 0 || !chest.isBlank(cellIndex)) {
                // Not a dial or the latch: fall through to the whole-chest outline (no custom renderer added).
                return;
            }
            hoverShape = CipherChestFaceLayout.dialHoverShape(facing, cellIndex);
        }

        event.addCustomRenderer((renderState, collector, poseStack, levelRenderState) -> {
            Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
            collector.submitShapeOutline(poseStack, hoverShape, RenderTypes.lines(), OUTLINE_COLOR, OUTLINE_WIDTH, renderState.isTranslucent());
            poseStack.popPose();
            return true;
        });
    }

    private RelictCipherChestRenderers() {
    }

}
