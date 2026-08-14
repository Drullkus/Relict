package us.drullk.relict;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import us.drullk.relict.client.renderer.RelictSkyRenderers;
import us.drullk.relict.client.renderer.vizard.VizardRenderers;

@Mod(value = Relict.MODID, dist = Dist.CLIENT)
public class RelictClient {

    public RelictClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(RelictClient::onClientSetup);
        RelictSkyRenderers.register(modEventBus);
        VizardRenderers.register(modEventBus);

        // FIXME uncomment once config entries are added
        //  container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)
    }

    static void onClientSetup(FMLClientSetupEvent event) {
    }

}
