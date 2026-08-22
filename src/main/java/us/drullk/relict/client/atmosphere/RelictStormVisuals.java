package us.drullk.relict.client.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import us.drullk.relict.RelictTags;
import us.drullk.relict.atmosphere.AtmosphereCurve.StormArc;

/**
 * Storm visual effects, all driven by {@code arcPhase x dustAxis} the same way sky darkening already is
 * (calm stays look clear even at high pressure; the storm itself is the visual event): ambient dust
 * particles, fog color darkening, and fog closing in. Sky darkening needed no new code — see
 * {@code MarsSkyboxRenderer}, which already reads the same {@code clientTau()}. There is no HUD vignette.
 * <p>
 * Hooked against real 26.2 NeoForge client APIs, verified in {@code neoforge-26.2.0.57-sources.jar}:
 * {@link ViewportEvent.ComputeFogColor} and {@link ViewportEvent.RenderFog} (both {@code NeoForge.EVENT_BUS},
 * client logical side only, fired every frame for whichever fog is currently rendering) and
 * {@link ClientTickEvent.Post} for the ambient particle spawner. All three gate on the Mars dimension and on
 * {@link RelictAtmosphere#isSynced()}, so a joining player never sees a flash of storm dust from a guessed
 * cycle.
 */
public final class RelictStormVisuals {

    /** Same dust tint {@code MarsSkyboxRenderer} blends the sky toward, so fog and sky read as one storm. */
    private static final int DUST_COLOR = ARGB.color(150, 100, 60);

    /** How far {@code arcTau x dustAxis} can push the fog color toward {@link #DUST_COLOR}. */
    private static final float FOG_COLOR_BLEND_MAX = 0.8F;

    /** Floor on the near/far fog scale at maximum storm intensity — visibility collapses but never to zero. */
    private static final float FOG_CONTRACTION_MIN_SCALE = 0.25F;

    /** Ambient dust particles: max spawn attempts per client tick at full storm intensity. */
    private static final int AMBIENT_PARTICLE_MAX_PER_TICK = 3;

    /** Horizontal/vertical radius around the camera particles spawn within. */
    private static final double AMBIENT_PARTICLE_RADIUS_XZ = 6.0;
    private static final double AMBIENT_PARTICLE_RADIUS_Y = 3.0;

    private static final RandomSource RANDOM = RandomSource.create();

    private RelictStormVisuals() {
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isOnMars()) {
            return;
        }

        StormArc arc = RelictAtmosphere.clientArc();
        float blend = Mth.clamp(arc.tau(), 0.0F, 1.0F) * FOG_COLOR_BLEND_MAX;
        if (blend <= 0.0F) {
            return;
        }

        float dustRed = ARGB.red(DUST_COLOR) / 255.0F;
        float dustGreen = ARGB.green(DUST_COLOR) / 255.0F;
        float dustBlue = ARGB.blue(DUST_COLOR) / 255.0F;

        event.setRed(Mth.lerp(blend, event.getRed(), dustRed));
        event.setGreen(Mth.lerp(blend, event.getGreen(), dustGreen));
        event.setBlue(Mth.lerp(blend, event.getBlue(), dustBlue));
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!isOnMars()) {
            return;
        }

        StormArc arc = RelictAtmosphere.clientArc();
        float intensity = Mth.clamp(arc.tau(), 0.0F, 1.0F);
        if (intensity <= 0.0F) {
            return;
        }

        float scale = Mth.lerp(intensity, 1.0F, FOG_CONTRACTION_MIN_SCALE);
        event.scaleNearPlaneDistance(scale);
        event.scaleFarPlaneDistance(scale);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null || !isOnMars()) {
            return;
        }

        StormArc arc = RelictAtmosphere.clientArc();
        float intensity = Mth.clamp(arc.tau(), 0.0F, 1.0F);
        if (intensity <= 0.0F) {
            return;
        }

        int attempts = Math.round(intensity * AMBIENT_PARTICLE_MAX_PER_TICK);
        for (int i = 0; i < attempts; i++) {
            double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 2.0 * AMBIENT_PARTICLE_RADIUS_XZ;
            double y = player.getY() + player.getEyeHeight() + (RANDOM.nextDouble() - 0.5) * 2.0 * AMBIENT_PARTICLE_RADIUS_Y;
            double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 2.0 * AMBIENT_PARTICLE_RADIUS_XZ;
            level.addParticle(ParticleTypes.ASH, x, y, z, 0.0, -0.01, 0.0);
        }
    }

    private static boolean isOnMars() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimensionTypeRegistration().is(RelictTags.REQUIRES_MARS_LIFE_SUPPORT) && RelictAtmosphere.isSynced();
    }

}
