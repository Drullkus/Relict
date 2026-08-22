package us.drullk.relict;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import us.drullk.relict.atmosphere.AtmosphereSyncPayload;
import us.drullk.relict.atmosphere.RelictAtmosphereServer;
import us.drullk.relict.atmosphere.RelictStormCommand;
import us.drullk.relict.init.RelictAttributes;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictCreativeTabs;
import us.drullk.relict.init.RelictDataComponents;
import us.drullk.relict.init.RelictEnvironmentAttributes;
import us.drullk.relict.init.RelictGameRules;
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
        RelictGameRules.register(modEventBus);
        RelictWorldgenTypes.register(modEventBus);

        modEventBus.addListener(new RelictCustomRegistries()::register);
        modEventBus.addListener(Relict::registerPayloads);

        RelictEvents events = new RelictEvents();
        NeoForge.EVENT_BUS.addListener(events::levelTick);
        NeoForge.EVENT_BUS.addListener(events::incomingElectricDamage);
        NeoForge.EVENT_BUS.addListener(events::mobEffectApplicable);

        RelictAtmosphereServer atmosphere = new RelictAtmosphereServer();
        NeoForge.EVENT_BUS.addListener(atmosphere::levelTick);
        NeoForge.EVENT_BUS.addListener(atmosphere::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(atmosphere::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(new RelictStormCommand()::register);

        // FIXME uncomment once config entries are added
        //  modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(AtmosphereSyncPayload.TYPE, AtmosphereSyncPayload.STREAM_CODEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

}
