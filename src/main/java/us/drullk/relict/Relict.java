package us.drullk.relict;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictCreativeTabs;
import us.drullk.relict.init.RelictEnvironmentAttributes;
import us.drullk.relict.init.RelictItems;

@Mod(Relict.MODID)
public class Relict {
    public static final String MODID = "relict";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Relict(IEventBus modEventBus, ModContainer modContainer) {
        RelictBlocks.BLOCKS.register(modEventBus);
        RelictItems.ITEMS.register(modEventBus);
        RelictCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        RelictEnvironmentAttributes.register(modEventBus);

        // FIXME uncomment once config entries are added
        //  modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

}
