package us.drullk.relict.client.renderer.vizard;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.init.RelictItems;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class VizardRenderers {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(VizardRenderers::onRegisterClientExtensions);
        modEventBus.addListener(VizardRenderers::onRegisterLayerDefinitions);
        modEventBus.addListener(VizardRenderers::onAddLayers);
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(VitalVizardClientExtensions.INSTANCE, RelictItems.VITAL_VIZARD.get(), RelictItems.SPENT_VIZARD.get());
    }

    private static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VitalVizardModel.HELMET, VitalVizardModel::createHelmetLayer);
        event.registerLayerDefinition(VitalVizardModel.ATMOSPHERE, VitalVizardModel::createAtmosphereLayer);
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        EntityModelSet models = event.getEntityModels();
        VitalVizardClientExtensions.setModels(models);

        ModelPart atmosphere = models.bakeLayer(VitalVizardModel.ATMOSPHERE);
        Set<EntityRenderer<?, ?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (PlayerModelType skin : event.getSkins()) {
            addAtmosphereLayer(event.getPlayerRenderer(skin), atmosphere, seen);
            addAtmosphereLayer(event.getMannequinRenderer(skin), atmosphere, seen);
        }

        for (EntityType<?> entityType : event.getEntityTypes()) {
            addAtmosphereLayer(event.getRenderer(entityType), atmosphere, seen);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addAtmosphereLayer(@Nullable EntityRenderer<?, ?> renderer, ModelPart atmosphere, Set<EntityRenderer<?, ?>> seen) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living) || !(living.getModel() instanceof HumanoidModel<?>) || !seen.add(renderer)) {
            return;
        }

        living.addLayer(new VizardAtmosphereLayer(living, atmosphere));
    }

}
