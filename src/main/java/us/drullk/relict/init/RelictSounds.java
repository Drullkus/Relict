package us.drullk.relict.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;

public class RelictSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Relict.MODID);

    // TODO make sound files
    public static final DeferredHolder<SoundEvent, SoundEvent> VIZARD_WARNING = register("vizard.warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> VIZARD_FAILED = register("vizard.failed");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Relict.id(path)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

}
