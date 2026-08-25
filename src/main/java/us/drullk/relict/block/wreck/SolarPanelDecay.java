package us.drullk.relict.block.wreck;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import us.drullk.relict.Relict;
import us.drullk.relict.RelictTags;
import us.drullk.relict.atmosphere.AtmosphereCurve;
import us.drullk.relict.atmosphere.AtmosphereCurve.CycleGeometry;
import us.drullk.relict.atmosphere.RelictAtmosphereData;
import us.drullk.relict.atmosphere.RelictAtmosphereServer;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictGameRules;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Decay and brush behavior shared by the four solar panel stage blocks. Modeled on vanilla copper
 * oxidation in spirit (a chain of blocks, each the next weathering stage) but with the dust storm as the
 * clock instead of a per-block random weathering roll: decay only progresses while a storm is depositing
 * dust AND the panel sits at or above the top of its column's heightmap (a roofed panel never dusts).
 */
public final class SolarPanelDecay {

    private SolarPanelDecay() {
    }

    /** Chance per qualifying random tick that a panel advances one stage. Producer-confirmed 2026-08-23; still flagged for later tuning. */
    public static final float DECAY_CHANCE = 0.15F;

    /**
     * Which heightmap answers "is this panel at the top of its column": WORLD_SURFACE counts every non-air
     * block, decorative or not, matching the producer's plain "any block above the panel" ruling. The
     * narrower MOTION_BLOCKING type would let a non-collidable block (a torch, a flower) sit above the
     * panel without roofing it, which is looser than the ruling asks for.
     */
    private static final Heightmap.Types SURFACE_HEIGHTMAP = Heightmap.Types.WORLD_SURFACE;

    /** Storm phases where dust is actually falling: the lead-in haze (DISTANT) and the silent impact instant (ARRIVAL) do not count. */
    private static boolean isDustFallingPhase(StormPhase phase) {
        return switch (phase) {
            case DUST_ENVELOPE, WIND_BUILD, ELECTRIC_PEAK, TAIL -> true;
            case CLEAR, DISTANT, ARRIVAL -> false;
        };
    }

    /** Pure: true once a rolled storm's arc has reached a dust-falling phase. */
    public static boolean isStormDepositingDust(StormPhase phase) {
        return isDustFallingPhase(phase);
    }

    /** Pure: true when nothing in the column stands above the panel's own position. */
    public static boolean isAtOrAboveSurface(int panelY, int surfaceHeight) {
        return surfaceHeight <= panelY + 1;
    }

    private static final Supplier<Map<Block, Block>> NEXT_BY_BLOCK = com.google.common.base.Suppliers.memoize(() -> ImmutableMap.<Block, Block>builder()
            .put(RelictBlocks.SOLAR_PANEL.get(), RelictBlocks.SOLAR_PANEL_SPRINKLED.get())
            .put(RelictBlocks.SOLAR_PANEL_SPRINKLED.get(), RelictBlocks.SOLAR_PANEL_DUSTED.get())
            .put(RelictBlocks.SOLAR_PANEL_DUSTED.get(), RelictBlocks.SOLAR_PANEL_SANDED.get())
            .build());

    /**
     * The brush's loot table for this stage, keyed by the stage being cleaned. Clean has no entry: brushing
     * it is a no-op, not a table with a zero-chance roll. Datagen ({@code WreckLootTables}, in the data
     * source set) generates the loot table JSON these keys point at, using these same key constants.
     */
    public static final ResourceKey<LootTable> BRUSH_SOLAR_PANEL_SPRINKLED = brushLootKey("wreck/brush/solar_panel_sprinkled");
    public static final ResourceKey<LootTable> BRUSH_SOLAR_PANEL_DUSTED = brushLootKey("wreck/brush/solar_panel_dusted");
    public static final ResourceKey<LootTable> BRUSH_SOLAR_PANEL_SANDED = brushLootKey("wreck/brush/solar_panel_sanded");

    private static final Supplier<Map<Block, ResourceKey<LootTable>>> BRUSH_LOOT_TABLE = com.google.common.base.Suppliers.memoize(() -> ImmutableMap.<Block, ResourceKey<LootTable>>builder()
            .put(RelictBlocks.SOLAR_PANEL_SPRINKLED.get(), BRUSH_SOLAR_PANEL_SPRINKLED)
            .put(RelictBlocks.SOLAR_PANEL_DUSTED.get(), BRUSH_SOLAR_PANEL_DUSTED)
            .put(RelictBlocks.SOLAR_PANEL_SANDED.get(), BRUSH_SOLAR_PANEL_SANDED)
            .build());

    private static ResourceKey<LootTable> brushLootKey(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Relict.id(path));
    }

    /** The next decay stage for this block, if any; SANDED (and any non-panel block) returns empty. */
    public static Optional<Block> next(Block block) {
        return Optional.ofNullable(NEXT_BY_BLOCK.get().get(block));
    }

    /** The brush's loot table key for this block, if it is a dusty stage. */
    public static Optional<ResourceKey<LootTable>> brushLootTable(Block block) {
        return Optional.ofNullable(BRUSH_LOOT_TABLE.get().get(block));
    }

    public static void tick(Block self, BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Block nextBlock = NEXT_BY_BLOCK.get().get(self);
        if (nextBlock == null || !level.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE)) {
            return;
        }

        if (!isStormDepositingDust(currentStormPhase(level))) {
            return;
        }

        int surfaceHeight = level.getHeight(SURFACE_HEIGHTMAP, pos.getX(), pos.getZ());
        if (!isAtOrAboveSurface(pos.getY(), surfaceHeight)) {
            return;
        }

        if (random.nextFloat() >= DECAY_CHANCE) {
            return;
        }

        level.setBlockAndUpdate(pos, nextBlock.defaultBlockState().setValue(SolarPanelBlock.WATERLOGGED, state.getValue(SolarPanelBlock.WATERLOGGED)));
    }

    /**
     * Restores any dusty stage to clean in one stroke, costs the brush 1 durability, and rolls the stage's
     * brush loot table (a {@code random_chance}-gated red sand entry). Brushing an already-clean panel (or
     * any non-panel block) is a no-op.
     *
     * @return true if this call cleaned a panel (for the caller's own bookkeeping; the brush's durability cost is applied here regardless).
     */
    public static boolean brush(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack brush) {
        ResourceKey<LootTable> lootTableKey = BRUSH_LOOT_TABLE.get().get(state.getBlock());
        if (lootTableKey == null) {
            return false;
        }

        Block clean = RelictBlocks.SOLAR_PANEL.get();
        level.setBlockAndUpdate(pos, clean.defaultBlockState().setValue(SolarPanelBlock.WATERLOGGED, state.getValue(SolarPanelBlock.WATERLOGGED)));

        EquipmentSlot slot = brush.equals(player.getItemBySlot(EquipmentSlot.OFFHAND)) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        brush.hurtAndBreak(1, player, slot);

        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withOptionalParameter(LootContextParams.TOOL, brush)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.BLOCK);

        for (ItemStack drop : lootTable.getRandomItems(params)) {
            Block.popResource(level, pos, drop);
        }

        return true;
    }

    /**
     * Reads the storm's current phase the same way {@link RelictAtmosphereServer} and
     * {@code /relictstorm status} do: the schedule off the server-wide saved data, the arc derived fresh
     * from it. No parallel storm-state tracking is introduced here.
     */
    private static StormPhase currentStormPhase(ServerLevel level) {
        MinecraftServer server = level.getServer();
        long totalTicks = RelictAtmosphereServer.marsTotalTicks(server);
        GameRules rules = server.getGlobalGameRules();
        int cycleTenthSols = rules.get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());
        CycleGeometry geo = AtmosphereCurve.geometryAt(totalTicks, RelictDimension.SOL_TICKS, cycleTenthSols);
        RelictAtmosphereData data = server.getDataStorage().computeIfAbsent(RelictAtmosphereData.TYPE);
        return AtmosphereCurve.stormAt(totalTicks, geo, data.schedule()).phase();
    }

}
