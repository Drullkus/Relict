package us.drullk.relict.client.renderer.vizard;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public class VitalVizardClientExtensions implements IClientItemExtensions {

    public static final VitalVizardClientExtensions INSTANCE = new VitalVizardClientExtensions();

    private static final Map<Model, VitalVizardModel> REPLACEMENTS = new IdentityHashMap<>();

    private static @Nullable EntityModelSet models;

    private VitalVizardClientExtensions() {
    }

    static void setModels(EntityModelSet entityModels) {
        models = entityModels;
        REPLACEMENTS.clear();
    }

    @Override
    public Model getHumanoidArmorModel(ItemStack itemStack, EquipmentClientInfo.LayerType layerType, Model original) {
        EntityModelSet entityModels = models;
        if (entityModels == null || layerType != EquipmentClientInfo.LayerType.HUMANOID || !(original instanceof HumanoidModel<?> humanoid)) {
            return original;
        }

        return REPLACEMENTS.computeIfAbsent(original,
                key -> new VitalVizardModel(entityModels.bakeLayer(VitalVizardModel.HELMET), humanoid));
    }

}
