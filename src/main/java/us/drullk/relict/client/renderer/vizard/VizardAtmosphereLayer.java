package us.drullk.relict.client.renderer.vizard;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import us.drullk.relict.client.renderer.RelictRenderPipelines;
import us.drullk.relict.init.RelictItems;

public class VizardAtmosphereLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {

    static final RenderType ATMOSPHERE = RenderType.create("vizard_atmosphere",
            RenderSetup.builder(RelictRenderPipelines.VIZARD_ATMOSPHERE).createRenderSetup());

    private static final int NOT_A_VIZARD = 0;
    private static final int LIVE_SKY = 0xFFFFFFFF;
    private static final int SPENT_SKY = 0xFF000000;

    private static final int ORDER = 0;

    private final ModelPart atmosphere;

    public VizardAtmosphereLayer(RenderLayerParent<S, M> renderer, ModelPart atmosphere) {
        super(renderer);
        this.atmosphere = atmosphere;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float yRot, float xRot) {
        int tint = tint(state.headEquipment);
        if (state.isInvisible || tint == NOT_A_VIZARD) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().head.translateAndRotate(poseStack);
        submitNodeCollector.order(ORDER)
                .submitModelPart(this.atmosphere, poseStack, ATMOSPHERE, lightCoords, OverlayTexture.NO_OVERLAY, null, tint, null, 0);
        poseStack.popPose();
    }

    private static int tint(ItemStack headEquipment) {
        if (headEquipment.is(RelictItems.VITAL_VIZARD.get())) {
            return LIVE_SKY;
        }

        if (headEquipment.is(RelictItems.SPENT_VIZARD.get())) {
            return SPENT_SKY;
        }

        return NOT_A_VIZARD;
    }

}
