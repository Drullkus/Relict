package us.drullk.relict.client.renderer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.common.NeoForge;
import us.drullk.relict.Relict;
import us.drullk.relict.client.renderer.sky.MarsSkyboxRenderer;

/**
 * Wires the Mars sky to the level.
 * <p>
 * The id registered here is the one the dimension type names through
 * {@code neoforge:custom_skybox}, which is how a level chooses a sky.
 * <p>
 * Clouds and weather are the other two renderers this event carries; both are held back until the storm
 * cycle exists, so Mars keeps vanilla behaviour for now.
 */
public class RelictSkyRenderers {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RelictSkyRenderers::onRegisterRenderers);
        modEventBus.addListener(RelictRenderPipelines::onRegisterPipelines);
        NeoForge.EVENT_BUS.addListener(MarsSkyboxRenderer::onExtractLevelRenderState);
    }

    private static void onRegisterRenderers(RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(Relict.id("mars"), MarsSkyboxRenderer.INSTANCE);
    }

}
