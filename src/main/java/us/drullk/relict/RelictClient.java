package us.drullk.relict;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.List;
import java.util.stream.IntStream;

@Mod(value = Relict.MODID, dist = Dist.CLIENT)
public class RelictClient {

    public static final List<Identifier> PHOBOS_PHASE_SPRITES;
    public static final List<Identifier> DEIMOS_PHASE_SPRITES;

    static {
        PHOBOS_PHASE_SPRITES = IntStream.range(0, RelictDimension.PHOBOS_PHASES)
                .mapToObj(frame1 -> Relict.id("%s/phase_%02d".formatted("phobos", frame1)))
                .toList();
        DEIMOS_PHASE_SPRITES = IntStream.range(0, RelictDimension.DEIMOS_PHASES)
                .mapToObj(frame -> Relict.id("%s/phase_%02d".formatted("deimos", frame)))
                .toList();
    }

    public RelictClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(RelictClient::onClientSetup);

        // FIXME uncomment once config entries are added
        //  container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)
    }

    static void onClientSetup(FMLClientSetupEvent event) {
    }

}
