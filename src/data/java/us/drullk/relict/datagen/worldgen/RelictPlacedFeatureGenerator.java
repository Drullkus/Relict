package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ClampedNormalInt;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.material.Fluids;
import us.drullk.relict.init.worldgen.RelictConfiguredFeatures;
import us.drullk.relict.init.worldgen.RelictPlacedFeatures;

import java.util.List;
import java.util.stream.Stream;

public class RelictPlacedFeatureGenerator {

    private static final PlacementModifier DEEP_BAND = HeightRangePlacement.uniform(VerticalAnchor.absolute(-54), VerticalAnchor.absolute(0));

    private static final PlacementModifier SHALLOW_BAND = HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(80));

    private static final PlacementModifier CAVE_BAND = HeightRangePlacement.uniform(VerticalAnchor.absolute(-54), VerticalAnchor.absolute(80));

    private static final PlacementModifier ON_CAVE_FLOOR = EnvironmentScanPlacement.scanningFor(
            Direction.DOWN, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 32);

    private static final UniformInt CAVE_SURFACE_ATTEMPTS = UniformInt.of(64, 128);

    private static final PlacementModifier DEEP_LAKE_BAND = HeightRangePlacement.uniform(VerticalAnchor.absolute(-32), VerticalAnchor.absolute(0));

    static final UniformInt SULFUR_GEYSER_ATTEMPTS = UniformInt.of(2, 5);
    private static final int SULFUR_DEEP_LAKE_INTERIOR_MULTIPLIER = 3;
    static final UniformInt SULFUR_DEEP_LAKE_INTERIOR_ATTEMPTS = UniformInt.of(
            SULFUR_GEYSER_ATTEMPTS.minInclusive() * SULFUR_DEEP_LAKE_INTERIOR_MULTIPLIER,
            SULFUR_GEYSER_ATTEMPTS.maxInclusive() * SULFUR_DEEP_LAKE_INTERIOR_MULTIPLIER);

    private static final int SULFUR_DEEP_LAKE_RARITY = 256;

    private static final UniformInt FROST_FLOOR_ATTEMPTS = UniformInt.of(96, 160);
    private static final int FROST_FLOOR_SCAN_STEPS = 24;

    private static final PlacementModifier NOT_NEAR_LAVA = BlockPredicateFilter.forPredicate(BlockPredicate.allOf(
            Stream.of(Direction.values())
                    .map(direction -> BlockPredicate.not(BlockPredicate.anyOf(
                            BlockPredicate.matchesFluids(direction.getUnitVec3i(), Fluids.LAVA, Fluids.FLOWING_LAVA),
                            BlockPredicate.matchesBlocks(direction.getUnitVec3i(), Blocks.MAGMA_BLOCK))))
                    .toList()));

    public static void bootstrapPlacedFeatures(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> features = context.lookup(Registries.CONFIGURED_FEATURE);

        register(context, RelictPlacedFeatures.SULFUR_GEODE, features.getOrThrow(RelictConfiguredFeatures.SULFUR_GEODE), RarityFilter.onAverageOnceEvery(48), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());

        basaltCavesPlacedFeatures(context, features);
        calciteCavesPlacedFeatures(context, features);
        sulfurCavesPlacedFeatures(context, features);
        iceCavesPlacedFeatures(context, features);
        igneousPocketsPlacedFeatures(context, features);

        DustLayerFeatureGenerator.bootstrapPlacedFeatures(context, features);
        RockFeatureGenerator.bootstrapPlacedFeatures(context, features);
    }

    private static void igneousPocketsPlacedFeatures(BootstrapContext<PlacedFeature> context, HolderGetter<ConfiguredFeature<?, ?>> features) {
        register(context, RelictPlacedFeatures.ANDESITE_POCKET_UPPER, features.getOrThrow(RelictConfiguredFeatures.ANDESITE_POCKET), RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(), SHALLOW_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ANDESITE_POCKET_LOWER, features.getOrThrow(RelictConfiguredFeatures.ANDESITE_POCKET), CountPlacement.of(2), InSquarePlacement.spread(), DEEP_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.GRANITE_POCKET_UPPER, features.getOrThrow(RelictConfiguredFeatures.GRANITE_POCKET), RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(), SHALLOW_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.GRANITE_POCKET_LOWER, features.getOrThrow(RelictConfiguredFeatures.GRANITE_POCKET), CountPlacement.of(2), InSquarePlacement.spread(), DEEP_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.DIORITE_POCKET_UPPER, features.getOrThrow(RelictConfiguredFeatures.DIORITE_POCKET), RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(), SHALLOW_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.DIORITE_POCKET_LOWER, features.getOrThrow(RelictConfiguredFeatures.DIORITE_POCKET), CountPlacement.of(2), InSquarePlacement.spread(), DEEP_BAND, BiomeFilter.biome());
    }

    private static void basaltCavesPlacedFeatures(BootstrapContext<PlacedFeature> context, HolderGetter<ConfiguredFeature<?, ?>> features) {
        register(context, RelictPlacedFeatures.BASALT_COLUMNS, features.getOrThrow(RelictConfiguredFeatures.BASALT_COLUMNS), CountPlacement.of(CAVE_SURFACE_ATTEMPTS), InSquarePlacement.spread(), CAVE_BAND, ON_CAVE_FLOOR, RandomOffsetPlacement.vertical(ConstantInt.of(1)), BiomeFilter.biome());
        register(context, RelictPlacedFeatures.BLACKSTONE_BLOBS, features.getOrThrow(RelictConfiguredFeatures.BLACKSTONE_BLOBS), CountPlacement.of(UniformInt.of(6, 12)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.GRAVEL_FLOOR, features.getOrThrow(RelictConfiguredFeatures.GRAVEL_FLOOR), CountPlacement.of(UniformInt.of(4, 8)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12), RandomOffsetPlacement.vertical(ConstantInt.of(-1)), BiomeFilter.biome());
        register(context, RelictPlacedFeatures.MAGMA_PATCH, features.getOrThrow(RelictConfiguredFeatures.MAGMA_PATCH), CountPlacement.of(UniformInt.of(2, 5)), InSquarePlacement.spread(), DEEP_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.SPRING_LAVA, features.getOrThrow(RelictConfiguredFeatures.SPRING_LAVA), CountPlacement.of(UniformInt.of(1, 3)), InSquarePlacement.spread(), DEEP_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.MEGABRECCIA, features.getOrThrow(RelictConfiguredFeatures.MEGABRECCIA), RarityFilter.onAverageOnceEvery(24), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
    }

    private static void calciteCavesPlacedFeatures(BootstrapContext<PlacedFeature> context, HolderGetter<ConfiguredFeature<?, ?>> features) {
        register(context, RelictPlacedFeatures.CALCITE_BLOBS, features.getOrThrow(RelictConfiguredFeatures.CALCITE_BLOBS), CountPlacement.of(UniformInt.of(4, 8)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.CALCITE_SPELEOTHEM_CLUSTER, features.getOrThrow(RelictConfiguredFeatures.CALCITE_SPELEOTHEM_CLUSTER), CountPlacement.of(UniformInt.of(24, 48)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.CALCITE_SPELEOTHEM, features.getOrThrow(RelictConfiguredFeatures.CALCITE_SPELEOTHEM), CountPlacement.of(UniformInt.of(96, 128)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, CountPlacement.of(UniformInt.of(1, 5)), RandomOffsetPlacement.of(ClampedNormalInt.of(0.0F, 3.0F, -10, 10), ClampedNormalInt.of(0.0F, 0.6F, -2, 2)), BiomeFilter.biome());
        register(context, RelictPlacedFeatures.CALCITE_LARGE_DRIPSTONE, features.getOrThrow(RelictConfiguredFeatures.CALCITE_LARGE_DRIPSTONE), CountPlacement.of(UniformInt.of(10, 24)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
    }

    private static void sulfurCavesPlacedFeatures(BootstrapContext<PlacedFeature> context, HolderGetter<ConfiguredFeature<?, ?>> features) {
        register(context, RelictPlacedFeatures.SULFUR_BLOBS, features.getOrThrow(RelictConfiguredFeatures.SULFUR_BLOBS), CountPlacement.of(UniformInt.of(4, 8)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.CINNABAR_BLOBS, features.getOrThrow(RelictConfiguredFeatures.CINNABAR_BLOBS), CountPlacement.of(UniformInt.of(3, 6)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.TUFF_SCATTER, features.getOrThrow(RelictConfiguredFeatures.TUFF_SCATTER), CountPlacement.of(UniformInt.of(2, 4)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.SULFUR_SPIKE_CLUSTER, features.getOrThrow(RelictConfiguredFeatures.SULFUR_SPIKE_CLUSTER), CountPlacement.of(UniformInt.of(24, 48)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.SULFUR_SPIKE, features.getOrThrow(RelictConfiguredFeatures.SULFUR_SPIKE), CountPlacement.of(UniformInt.of(96, 128)), InSquarePlacement.spread(), PlacementUtils.RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT, CountPlacement.of(UniformInt.of(1, 5)), RandomOffsetPlacement.of(ClampedNormalInt.of(0.0F, 3.0F, -10, 10), ClampedNormalInt.of(0.0F, 0.6F, -2, 2)), BiomeFilter.biome());
        register(context, RelictPlacedFeatures.SULFUR_POOL, features.getOrThrow(RelictConfiguredFeatures.SULFUR_POOL), RarityFilter.onAverageOnceEvery(24), InSquarePlacement.spread(), DEEP_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.SULFUR_GEYSER, features.getOrThrow(RelictConfiguredFeatures.SULFUR_GEYSER), CountPlacement.of(SULFUR_GEYSER_ATTEMPTS), InSquarePlacement.spread(), DEEP_BAND, EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.matchesBlocks(Blocks.MAGMA_BLOCK), BlockPredicate.ONLY_IN_AIR_PREDICATE, 6), RandomOffsetPlacement.vertical(ConstantInt.of(1)), BiomeFilter.biome());
        register(context, RelictPlacedFeatures.SULFUR_DEEP_LAKE, features.getOrThrow(RelictConfiguredFeatures.SULFUR_DEEP_LAKE), RarityFilter.onAverageOnceEvery(SULFUR_DEEP_LAKE_RARITY), InSquarePlacement.spread(), DEEP_LAKE_BAND, BiomeFilter.biome());
    }

    private static void iceCavesPlacedFeatures(BootstrapContext<PlacedFeature> context, HolderGetter<ConfiguredFeature<?, ?>> features) {
        register(context, RelictPlacedFeatures.PACKED_ICE_LENS, features.getOrThrow(RelictConfiguredFeatures.PACKED_ICE_LENS), CountPlacement.of(UniformInt.of(3, 6)), InSquarePlacement.spread(), SHALLOW_BAND, NOT_NEAR_LAVA, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ICE_MARGIN, features.getOrThrow(RelictConfiguredFeatures.ICE_MARGIN), CountPlacement.of(UniformInt.of(4, 8)), InSquarePlacement.spread(), SHALLOW_BAND, NOT_NEAR_LAVA, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.BLUE_ICE_CORE, features.getOrThrow(RelictConfiguredFeatures.BLUE_ICE_CORE), RarityFilter.onAverageOnceEvery(32), InSquarePlacement.spread(), SHALLOW_BAND, NOT_NEAR_LAVA, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.FROST_FLOOR, features.getOrThrow(RelictConfiguredFeatures.FROST_FLOOR), CountPlacement.of(FROST_FLOOR_ATTEMPTS), InSquarePlacement.spread(), SHALLOW_BAND, EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, FROST_FLOOR_SCAN_STEPS), RandomOffsetPlacement.vertical(ConstantInt.of(1)), NOT_NEAR_LAVA, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ICE_LENS_RIM, features.getOrThrow(RelictConfiguredFeatures.ICE_LENS_RIM), CountPlacement.of(CAVE_SURFACE_ATTEMPTS), InSquarePlacement.spread(), SHALLOW_BAND, BiomeFilter.biome());
        register(context, RelictPlacedFeatures.ICE_WALL_POCKET, features.getOrThrow(RelictConfiguredFeatures.ICE_WALL_POCKET), CountPlacement.of(UniformInt.of(12, 24)), InSquarePlacement.spread(), SHALLOW_BAND, BiomeFilter.biome());
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> feature, PlacementModifier... modifiers) {
        context.register(key, new PlacedFeature(feature, List.of(modifiers)));
    }

}
