package us.drullk.relict;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import us.drullk.relict.init.RelictAttributes;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictCreativeTabs;
import us.drullk.relict.init.RelictDataComponents;
import us.drullk.relict.init.RelictEnvironmentAttributes;
import us.drullk.relict.init.RelictItems;
import us.drullk.relict.init.RelictSounds;
import us.drullk.relict.init.custom.RelictCustomRegistries;
import us.drullk.relict.init.worldgen.RelictWorldgenTypes;

@Mod(Relict.MODID)
public class Relict {
    public static final String MODID = "relict";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Relict(IEventBus modEventBus, ModContainer modContainer) {
        RelictBlocks.BLOCKS.register(modEventBus);
        RelictItems.ITEMS.register(modEventBus);
        RelictCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        RelictAttributes.register(modEventBus);
        RelictDataComponents.register(modEventBus);
        RelictSounds.register(modEventBus);
        RelictEnvironmentAttributes.register(modEventBus);
        RelictWorldgenTypes.register(modEventBus);

        modEventBus.addListener(new RelictCustomRegistries()::register);

        RelictEvents events = new RelictEvents();
        NeoForge.EVENT_BUS.addListener(events::levelTick);
        NeoForge.EVENT_BUS.addListener(events::incomingElectricDamage);
        NeoForge.EVENT_BUS.addListener(events::mobEffectApplicable);

        // FIXME uncomment once config entries are added
        //  modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

}
