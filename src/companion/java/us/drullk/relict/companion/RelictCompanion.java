package us.drullk.relict.companion;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import us.drullk.relict.Relict;

/**
 * Dev-client capture companion entrypoint in Relict's namespace. Excluded from production.
 */
@Mod(value = Relict.MODID, dist = Dist.CLIENT)
public final class RelictCompanion {

    public RelictCompanion(IEventBus modEventBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(CompanionBridge::onClientTick);
    }

}
