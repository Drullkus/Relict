package us.drullk.relict.companion;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * A containerized (headless, software-rendered) client's light engine can fail to propagate skylight onto
 * command-placed blocks. Since this module runs in-process, it can force the client's own render-facing
 * light engine to recheck a position right after staging, instead of hoping a later chunk reload fixes
 * it. Reports the resulting raw sky light either way -- this is a best-effort forcing pass, not a
 * guaranteed fix, and {@code stage}'s response is honest about the number it reads back rather than
 * asserting success.
 */
final class RelightStep {

    private static final int MAX_ITERATIONS = 200;

    private RelightStep() {
    }

    /** Operates on the CLIENT's own {@link ClientLevel} light engine, not the integrated server's --
     * what the screenshot captures is the client's render state, and "the containerized client's light
     * engine" is literally what the known limitation names. */
    static Step forceAndReport(BlockPos surface, String responseKey) {
        return Steps.once(ctx -> {
            ClientLevel level = ctx.level();
            LevelLightEngine lightEngine = level.getLightEngine();
            lightEngine.checkBlock(surface);
            int iterations = 0;
            while (lightEngine.hasLightWork() && iterations++ < MAX_ITERATIONS) {
                lightEngine.runLightUpdates();
            }
            int skyLight = level.getBrightness(LightLayer.SKY, surface);
            ctx.extra.addProperty(responseKey, skyLight);
        });
    }

}
