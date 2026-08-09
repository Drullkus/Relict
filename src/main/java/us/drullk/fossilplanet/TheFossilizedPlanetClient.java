package us.drullk.fossilplanet;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = TheFossilizedPlanet.MODID, dist = Dist.CLIENT)
public class TheFossilizedPlanetClient {

    public TheFossilizedPlanetClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(TheFossilizedPlanetClient::onClientSetup);

        // FIXME uncomment once config entries are added
        //  container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)
    }

    static void onClientSetup(FMLClientSetupEvent event) {
    }

}
