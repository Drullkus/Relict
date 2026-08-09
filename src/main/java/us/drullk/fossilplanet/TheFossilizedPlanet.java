package us.drullk.fossilplanet;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import us.drullk.fossilplanet.init.TFPBlocks;
import us.drullk.fossilplanet.init.TFPCreativeTabs;
import us.drullk.fossilplanet.init.TFPItems;

@Mod(TheFossilizedPlanet.MODID)
public class TheFossilizedPlanet {
    public static final String MODID = "the_fossilized_planet";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheFossilizedPlanet(IEventBus modEventBus, ModContainer modContainer) {
        TFPBlocks.BLOCKS.register(modEventBus);
        TFPItems.ITEMS.register(modEventBus);
        TFPCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // FIXME uncomment once config entries are added
        //  modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

}
