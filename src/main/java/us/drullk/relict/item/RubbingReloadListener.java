package us.drullk.relict.item;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.Relict;

import java.io.IOException;
import java.io.InputStream;

/**
 * Rebuilds {@link RubbingMapData}'s singleton from the active (override-aware) server resource manager on
 * every datapack (re)load -- {@code /reload} and world join both go through this.
 */
@EventBusSubscriber(modid = Relict.MODID)
public class RubbingReloadListener extends SimplePreparableReloadListener<byte[]> {

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Relict.id("rubbing_map_data"), new RubbingReloadListener());
    }

    @Override
    @Nullable
    protected byte[] prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        try (InputStream stream = resourceManager.open(RubbingMapData.PAYLOAD_ID)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            Relict.LOGGER.warn("Cipher chest rubbing payload {} missing from the active datapacks; keeping the jar-shipped default", RubbingMapData.PAYLOAD_ID);
            return null;
        }
    }

    @Override
    protected void apply(@Nullable byte[] payload, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (payload != null) {
            RubbingMapData.reload(payload);
        }
    }

}
