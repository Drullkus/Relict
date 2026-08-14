package us.drullk.relict.client.renderer.vizard;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.Direction;
import us.drullk.relict.Relict;

import java.util.EnumSet;

public class VitalVizardModel extends HumanoidModel<HumanoidRenderState> {

    public static final ModelLayerLocation HELMET = new ModelLayerLocation(Relict.id("vital_vizard"), "main");
    public static final ModelLayerLocation ATMOSPHERE = new ModelLayerLocation(Relict.id("vital_vizard"), "atmosphere");

    private static final CubeDeformation HALF_HEIGHT_SHELL = new CubeDeformation(1.0F, 0.5F, 1.0F);

    private static final CubeDeformation SHELL = new CubeDeformation(1.0F);

    private static final EnumSet<Direction> DOME_FACES = EnumSet.complementOf(EnumSet.of(Direction.UP));

    private final HumanoidModel<HumanoidRenderState> original;

    @SuppressWarnings("unchecked")
    public VitalVizardModel(ModelPart root, HumanoidModel<?> original) {
        super(root);
        this.original = (HumanoidModel<HumanoidRenderState>) original;
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        this.original.setupAnim(state);
        copyPose(this.original.head, this.head);
    }

    private static void copyPose(ModelPart from, ModelPart to) {
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
        to.xScale = from.xScale;
        to.yScale = from.yScale;
        to.zScale = from.zScale;
        to.visible = from.visible;
    }

    public static LayerDefinition createHelmetLayer() {
        MeshDefinition mesh = HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5F), SHELL).head();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, SHELL)
                        .texOffs(32, 0)
                        .addBox(-4.0F, -0.5F, -4.0F, 8.0F, 1.0F, 8.0F, HALF_HEIGHT_SHELL),
                PartPose.ZERO);

        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 32);
    }

    public static LayerDefinition createAtmosphereLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("atmosphere", CubeListBuilder.create()
                .addBox(-5.0F, -9.0F, -5.0F, 10.0F, 8.0F, 10.0F, DOME_FACES), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

}
