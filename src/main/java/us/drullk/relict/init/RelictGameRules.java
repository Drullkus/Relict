package us.drullk.relict.init;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;

/**
 * Gamerules for the atmosphere/storm clock, exposed as gamerule/datapack values from day one.
 */
public class RelictGameRules {

    public static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, Relict.MODID);

    public static final GameRuleCategory ATMOSPHERE = GameRuleCategory.register(Relict.id("atmosphere"));

    /**
     * Full atmosphere cycle length, in tenths of a sol — an int-sols gamerule can't express a fractional
     * cycle length. Default 25 = 2.5 sols = 55 minutes at {@code SOL_TICKS = 26400}.
     */
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> ATMOSPHERE_CYCLE_TENTH_SOLS =
            registerInt("atmosphere_cycle_tenth_sols", 25, 5, 500);

    /**
     * Percent chance an atmosphere stay gets a storm at all: {@code chance = min(1, percent / 100)}. One storm is
     * rolled per stay, so this gates whether that roll happens rather than scaling a per-check rate.
     */
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> STORM_FREQUENCY_PERCENT =
            registerInt("storm_frequency_percent", 98, 0, 100);

    /** Master toggle for triboelectric discharge damage; the storm still runs its full visual/audio arc when off. */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> STORM_DAMAGE =
            registerBoolean("storm_damage", true);

    private static DeferredHolder<GameRule<?>, GameRule<Integer>> registerInt(String id, int defaultValue, int min, int max) {
        return GAME_RULES.register(id, () -> new GameRule<>(ATMOSPHERE, GameRuleType.INT, IntegerArgumentType.integer(min, max),
                GameRuleTypeVisitor::visitInteger, Codec.intRange(min, max), i -> i, defaultValue, FeatureFlagSet.of()));
    }

    private static DeferredHolder<GameRule<?>, GameRule<Boolean>> registerBoolean(String id, boolean defaultValue) {
        return GAME_RULES.register(id, () -> new GameRule<>(ATMOSPHERE, GameRuleType.BOOL, BoolArgumentType.bool(),
                GameRuleTypeVisitor::visitBoolean, Codec.BOOL, b -> b ? 1 : 0, defaultValue, FeatureFlagSet.of()));
    }

    public static void register(IEventBus modEventBus) {
        GAME_RULES.register(modEventBus);
    }

}
