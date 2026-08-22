package us.drullk.relict.init;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;

public class RelictSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Relict.MODID);

    // TODO make sound files
    public static final DeferredHolder<SoundEvent, SoundEvent> VIZARD_WARNING = register("vizard.warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> VIZARD_FAILED = register("vizard.failed");

    // TODO make sound files
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_LOW = register("atmosphere.wind_low");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_GUST = register("atmosphere.wind_gust");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUST_TICK = register("atmosphere.dust_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_ARRIVAL = register("atmosphere.storm_arrival");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_LEAD_IN = register("atmosphere.storm_lead_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_PEAK = register("atmosphere.storm_peak");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISCHARGE_CORONA = register("atmosphere.discharge_corona");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISCHARGE_SNAP = register("atmosphere.discharge_snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISCHARGE_FIELD = register("atmosphere.discharge_field");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUST_DEVIL = register("atmosphere.dust_devil");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Relict.id(path)));
    }

    /**
     * The one call-site every {@code atmosphere.*} hook goes through. A no-op today (see class doc) so it
     * produces zero log spam and zero vanilla fallback noise with no audio files present.
     */
    @SuppressWarnings("unused")
    public static void fire(DeferredHolder<SoundEvent, SoundEvent> hook, Level level, BlockPos pos, SoundSource source, float volume, float pitch) {
        // TODO: once sounds.json carries this event, replace with
        //  level.playSound(null, pos, hook.get(), source, volume, pitch);
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

}
