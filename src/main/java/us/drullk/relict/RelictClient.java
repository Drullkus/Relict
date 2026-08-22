package us.drullk.relict;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import us.drullk.relict.atmosphere.AtmosphereSyncPayload;
import us.drullk.relict.client.atmosphere.RelictAtmosphere;
import us.drullk.relict.client.atmosphere.RelictStormVisuals;
import us.drullk.relict.client.renderer.RelictSkyRenderers;
import us.drullk.relict.client.renderer.vizard.VizardRenderers;

@Mod(value = Relict.MODID, dist = Dist.CLIENT)
public class RelictClient {

    public RelictClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(RelictClient::onClientSetup);
        modEventBus.addListener(RelictClient::registerClientPayloadHandlers);
        RelictSkyRenderers.register(modEventBus);
        VizardRenderers.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(RelictStormVisuals::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(RelictStormVisuals::onRenderFog);
        NeoForge.EVENT_BUS.addListener(RelictStormVisuals::onClientTick);

        // FIXME uncomment once config entries are added
        //  container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)
    }

    static void onClientSetup(FMLClientSetupEvent event) {
    }

    private static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(AtmosphereSyncPayload.TYPE, (payload, _) -> RelictAtmosphere.handleSync(payload));
    }

}
