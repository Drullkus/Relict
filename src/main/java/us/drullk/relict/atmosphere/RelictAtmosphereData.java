package us.drullk.relict.atmosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import us.drullk.relict.Relict;

/**
 * The one shared storm state for the whole server: one season, one set of storm events, shared by every
 * player on Mars. Held on {@code MinecraftServer.getDataStorage()} — the same genuinely server-global store
 * vanilla's own {@code ServerClockManager} uses ({@code data/relict/atmosphere.dat} at the save root,
 * alongside {@code data/minecraft/world_clocks.dat}) — rather than a per-dimension one, so it stays shared
 * even if nobody is currently on Mars to tick it.
 * <p>
 * Stores the stay's whole {@link StormSchedule} rather than a present-tense {@code stormPhase} /
 * {@code phaseStartTick} pair — phase and {@code tau} are pure functions of the schedule, derived fresh by
 * {@link AtmosphereCurve#stormAt} on both sides. Seasonal {@code pressure} is still not stored here either:
 * it is a pure function of the Mars clock's total ticks, recomputed identically on both sides.
 * <p>
 * The {@code schedule} field uses {@code optionalFieldOf}, so an {@code atmosphere.dat} missing it loads as
 * "no schedule for this cycle" and self-heals on the next tick. No DataFixer work needed.
 */
public class RelictAtmosphereData extends SavedData {

    public static final SavedDataType<RelictAtmosphereData> TYPE = new SavedDataType<>(
            Relict.id("atmosphere"),
            RelictAtmosphereData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                            StormSchedule.CODEC.optionalFieldOf("schedule", StormSchedule.NONE).forGetter(d -> d.schedule))
                    .apply(instance, RelictAtmosphereData::new)),
            DataFixTypes.LEVEL);

    private StormSchedule schedule = StormSchedule.NONE;

    public RelictAtmosphereData() {
    }

    private RelictAtmosphereData(StormSchedule schedule) {
        this.schedule = schedule;
    }

    public StormSchedule schedule() {
        return this.schedule;
    }

    public void setSchedule(StormSchedule schedule) {
        this.schedule = schedule;
        this.setDirty();
    }

}
