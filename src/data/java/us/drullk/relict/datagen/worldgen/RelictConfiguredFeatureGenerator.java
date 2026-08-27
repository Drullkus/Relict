package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ClampedInt;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PotentSulfurBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PotentSulfurState;
import net.minecraft.world.level.levelgen.GeodeBlockSettings;
import net.minecraft.world.level.levelgen.GeodeCrackSettings;
import net.minecraft.world.level.levelgen.GeodeLayerSettings;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.CompositeFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.LargeDripstoneConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SpeleothemClusterConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SpeleothemConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraft.world.level.material.Fluids;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.worldgen.RelictConfiguredFeatures;

import java.util.List;

public class RelictConfiguredFeatureGenerator {

    private static final RuleTest SMOOTH_BASALT = new BlockMatchTest(Blocks.SMOOTH_BASALT);
    private static final RuleTest PACKED_ICE = new BlockMatchTest(Blocks.PACKED_ICE);
    private static final RuleTest BASE_STONE_MARS = new TagMatchTest(RelictTags.BASE_STONE_MARS);

    private static final int IGNEOUS_POCKET_SIZE = 64;

    private static final int ICE_LENS_RIM_SIZE = 12;
    private static final int ICE_WALL_POCKET_SIZE = 4;
    private static final int CAVE_SURFACE_SCAN = 12;

    private static final float SPELEOTHEM_TALLER_CHANCE = 0.2F;
    private static final float SPELEOTHEM_DIRECTIONAL_SPREAD = 0.7F;
    private static final float SPELEOTHEM_SPREAD_2 = 0.5F;
    private static final float SPELEOTHEM_SPREAD_3 = 0.5F;

    public static void bootstrapConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);
        HolderSet<Block> speleothemReplaceable = blocks.getOrThrow(RelictTags.SPELEOTHEM_REPLACEABLE);
        HolderSet<Block> dripstoneReplaceable = blocks.getOrThrow(RelictTags.DRIPSTONE_REPLACEABLE);

        register(context, RelictConfiguredFeatures.SULFUR_GEODE, Feature.GEODE, new GeodeConfiguration(
                new GeodeBlockSettings(
                        BlockStateProvider.simple(Blocks.AIR),
                        BlockStateProvider.simple(Blocks.SULFUR),
                        BlockStateProvider.simple(Blocks.CINNABAR),
                        BlockStateProvider.simple(Blocks.CALCITE),
                        BlockStateProvider.simple(Blocks.SMOOTH_BASALT),
                        List.of(Blocks.SULFUR.defaultBlockState(), Blocks.CINNABAR.defaultBlockState()),
                        blocks.getOrThrow(BlockTags.FEATURES_CANNOT_REPLACE),
                        blocks.getOrThrow(BlockTags.GEODE_INVALID_BLOCKS)
                ),
                new GeodeLayerSettings(1.7, 2.2, 3.2, 4.2),
                new GeodeCrackSettings(0.95, 2.0, 2),
                0.35, 0.083, true, UniformInt.of(4, 6), UniformInt.of(3, 4), UniformInt.of(1, 2),
                -16, 16, 0.05, 1
        ));

        basaltCavesConfiguredFeatures(context, configuredFeatures, speleothemReplaceable);
        calciteCavesConfiguredFeatures(context, configuredFeatures, dripstoneReplaceable);
        sulfurCavesConfiguredFeatures(context, configuredFeatures, speleothemReplaceable);
        iceCavesConfiguredFeatures(context, configuredFeatures, speleothemReplaceable);
        igneousPockets(context);

        DustLayerFeatureGenerator.bootstrapConfiguredFeatures(context);
        RockFeatureGenerator.bootstrapConfiguredFeatures(context);
    }

    /** Overworld-style andesite/granite/diorite pockets (report §6), shared across all four underground biomes. */
    private static void igneousPockets(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        register(context, RelictConfiguredFeatures.ANDESITE_POCKET, Feature.ORE, new OreConfiguration(BASE_STONE_MARS, Blocks.ANDESITE.defaultBlockState(), IGNEOUS_POCKET_SIZE));
        register(context, RelictConfiguredFeatures.GRANITE_POCKET, Feature.ORE, new OreConfiguration(BASE_STONE_MARS, Blocks.GRANITE.defaultBlockState(), IGNEOUS_POCKET_SIZE));
        register(context, RelictConfiguredFeatures.DIORITE_POCKET, Feature.ORE, new OreConfiguration(BASE_STONE_MARS, Blocks.DIORITE.defaultBlockState(), IGNEOUS_POCKET_SIZE));
    }

    private static void basaltCavesConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context, HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures, HolderSet<Block> speleothemReplaceable) {
        register(context, RelictConfiguredFeatures.BASALT_COLUMNS, Feature.BASALT_COLUMNS, new ColumnFeatureConfiguration(UniformInt.of(2, 3), UniformInt.of(3, 8)));

        register(context, RelictConfiguredFeatures.BLACKSTONE_BLOBS, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.BLACKSTONE.defaultBlockState(), 24));

        register(context, RelictConfiguredFeatures.GRAVEL_FLOOR, Feature.DISK, new DiskConfiguration(BlockStateProvider.simple(Blocks.GRAVEL), BlockPredicate.matchesBlocks(Blocks.SMOOTH_BASALT), UniformInt.of(2, 4), 1));

        register(context, RelictConfiguredFeatures.MAGMA_PATCH, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.MAGMA_BLOCK.defaultBlockState(), 12));

        register(context, RelictConfiguredFeatures.SPRING_LAVA, Feature.SPRING, new SpringConfiguration(Fluids.LAVA.defaultFluidState(), true, 4, 1, HolderSet.direct(Block::builtInRegistryHolder, Blocks.SMOOTH_BASALT, Blocks.BASALT, Blocks.BLACKSTONE)));
    }

    private static void calciteCavesConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context, HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures, HolderSet<Block> dripstoneReplaceable) {
        register(context, RelictConfiguredFeatures.MEGABRECCIA_COBBLED, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 10));
        register(context, RelictConfiguredFeatures.MEGABRECCIA_TUFF, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.TUFF.defaultBlockState(), 10));
        register(context, RelictConfiguredFeatures.MEGABRECCIA, Feature.SIMPLE_RANDOM_SELECTOR, new CompositeFeatureConfiguration(HolderSet.direct(PlacementUtils.inlinePlaced(configuredFeatures.getOrThrow(RelictConfiguredFeatures.MEGABRECCIA_COBBLED)), PlacementUtils.inlinePlaced(configuredFeatures.getOrThrow(RelictConfiguredFeatures.MEGABRECCIA_TUFF)))));

        register(context, RelictConfiguredFeatures.CALCITE_BLOBS, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.CALCITE.defaultBlockState(), 20));

        register(context, RelictConfiguredFeatures.CALCITE_SPELEOTHEM_CLUSTER, Feature.SPELEOTHEM_CLUSTER, new SpeleothemClusterConfiguration(Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Blocks.POINTED_DRIPSTONE.defaultBlockState(), dripstoneReplaceable, 12, UniformInt.of(1, 4), UniformInt.of(2, 8), 1, 3, UniformInt.of(2, 4), UniformFloat.of(0.3F, 0.7F), ConstantFloat.ZERO, 0.1F, 3, 8));
        register(context, RelictConfiguredFeatures.CALCITE_SPELEOTHEM, Feature.SIMPLE_RANDOM_SELECTOR, new CompositeFeatureConfiguration(HolderSet.direct(speleothemDirection(Direction.DOWN, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Blocks.POINTED_DRIPSTONE.defaultBlockState(), dripstoneReplaceable), speleothemDirection(Direction.UP, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Blocks.POINTED_DRIPSTONE.defaultBlockState(), dripstoneReplaceable))));

        register(context, RelictConfiguredFeatures.CALCITE_LARGE_DRIPSTONE, Feature.LARGE_DRIPSTONE, new LargeDripstoneConfiguration(dripstoneReplaceable, 30, ClampedInt.of(UniformInt.of(3, 19), 3, 16), UniformFloat.of(0.4F, 2.0F), 0.33F, UniformFloat.of(0.3F, 0.9F), UniformFloat.of(0.4F, 1.0F), UniformFloat.of(0.0F, 0.3F), 4, 0.6F));
    }

    private static void sulfurCavesConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context, HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures, HolderSet<Block> speleothemReplaceable) {
        register(context, RelictConfiguredFeatures.SULFUR_BLOBS, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.SULFUR.defaultBlockState(), 18));
        register(context, RelictConfiguredFeatures.CINNABAR_BLOBS, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.CINNABAR.defaultBlockState(), 14));
        register(context, RelictConfiguredFeatures.TUFF_SCATTER, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.TUFF.defaultBlockState(), 20));

        register(context, RelictConfiguredFeatures.SULFUR_SPIKE_CLUSTER, Feature.SPELEOTHEM_CLUSTER, new SpeleothemClusterConfiguration(Blocks.SULFUR.defaultBlockState(), Blocks.SULFUR_SPIKE.defaultBlockState(), speleothemReplaceable, 12, UniformInt.of(1, 4), UniformInt.of(2, 8), 1, 3, UniformInt.of(2, 4), UniformFloat.of(0.3F, 0.7F), ConstantFloat.ZERO, 0.1F, 3, 8));
        register(context, RelictConfiguredFeatures.SULFUR_SPIKE, Feature.SIMPLE_RANDOM_SELECTOR, new CompositeFeatureConfiguration(HolderSet.direct(speleothemDirection(Direction.DOWN, Blocks.SULFUR.defaultBlockState(), Blocks.SULFUR_SPIKE.defaultBlockState(), speleothemReplaceable), speleothemDirection(Direction.UP, Blocks.SULFUR.defaultBlockState(), Blocks.SULFUR_SPIKE.defaultBlockState(), speleothemReplaceable))));

        register(context, RelictConfiguredFeatures.SULFUR_POOL, Feature.SEQUENCE, new CompositeFeatureConfiguration(HolderSet.direct(PlacementUtils.inlinePlaced(Feature.LAKE, new LakeFeature.Configuration(BlockStateProvider.simple(Blocks.WATER.defaultBlockState()), BlockStateProvider.simple(Blocks.SULFUR.defaultBlockState()), BlockPredicate.not(BlockPredicate.matchesBlocks(Blocks.SULFUR_SPIKE)), BlockPredicate.not(BlockPredicate.matchesTag(BlockTags.FEATURES_CANNOT_REPLACE)), BlockPredicate.not(BlockPredicate.matchesTag(BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE)))), PlacementUtils.inlinePlaced(Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.POTENT_SULFUR.defaultBlockState().setValue(PotentSulfurBlock.STATE, PotentSulfurState.WET))), EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.allOf(BlockPredicate.solid(), BlockPredicate.matchesFluids(Direction.UP.getUnitVec3i(), Fluids.WATER)), 4)))));

        register(context, RelictConfiguredFeatures.SULFUR_GEYSER, Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.POTENT_SULFUR.defaultBlockState())));
    }

    private static void iceCavesConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context, HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures, HolderSet<Block> speleothemReplaceable) {
        register(context, RelictConfiguredFeatures.PACKED_ICE_LENS, Feature.DISK, new DiskConfiguration(BlockStateProvider.simple(Blocks.PACKED_ICE), BlockPredicate.matchesBlocks(Blocks.SMOOTH_BASALT), UniformInt.of(3, 6), 2));
        register(context, RelictConfiguredFeatures.ICE_MARGIN, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.ICE.defaultBlockState(), 10));
        register(context, RelictConfiguredFeatures.BLUE_ICE_CORE, Feature.ORE, new OreConfiguration(SMOOTH_BASALT, Blocks.BLUE_ICE.defaultBlockState(), 6));
        register(context, RelictConfiguredFeatures.FROST_FLOOR, Feature.SIMPLE_BLOCK, new SimpleBlockConfiguration(BlockStateProvider.simple(Blocks.SNOW.defaultBlockState())));

        register(context, RelictConfiguredFeatures.ICE_LENS_RIM, Feature.SIMPLE_RANDOM_SELECTOR, new CompositeFeatureConfiguration(HolderSet.direct(
                caveSkinOfIce(Direction.DOWN, PACKED_ICE, Blocks.PACKED_ICE, ICE_LENS_RIM_SIZE),
                caveSkinOfIce(Direction.UP, PACKED_ICE, Blocks.PACKED_ICE, ICE_LENS_RIM_SIZE))));

        register(context, RelictConfiguredFeatures.ICE_WALL_POCKET, Feature.SIMPLE_RANDOM_SELECTOR, new CompositeFeatureConfiguration(HolderSet.direct(
                caveSkinOfIce(Direction.DOWN, SMOOTH_BASALT, Blocks.SMOOTH_BASALT, ICE_WALL_POCKET_SIZE),
                caveSkinOfIce(Direction.UP, SMOOTH_BASALT, Blocks.SMOOTH_BASALT, ICE_WALL_POCKET_SIZE))));
    }

    private static Holder<PlacedFeature> caveSkinOfIce(Direction scan, RuleTest host, Block hostBlock, int size) {
        return PlacementUtils.inlinePlaced(Feature.ORE, new OreConfiguration(host, Blocks.ICE.defaultBlockState(), size),
                EnvironmentScanPlacement.scanningFor(scan, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, CAVE_SURFACE_SCAN),
                BlockPredicateFilter.forPredicate(BlockPredicate.matchesBlocks(hostBlock)));
    }

    private static Holder<PlacedFeature> speleothemDirection(Direction tip, BlockState base, BlockState pointed, HolderSet<Block> replaceable) {
        Direction scan = tip == Direction.DOWN ? Direction.DOWN : Direction.UP;
        int verticalOffset = tip == Direction.DOWN ? 1 : -1;
        return PlacementUtils.inlinePlaced(Feature.SPELEOTHEM, new SpeleothemConfiguration(base, pointed, replaceable, SPELEOTHEM_TALLER_CHANCE, SPELEOTHEM_DIRECTIONAL_SPREAD, SPELEOTHEM_SPREAD_2, SPELEOTHEM_SPREAD_3), EnvironmentScanPlacement.scanningFor(scan, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_OR_WATER_PREDICATE, 12), RandomOffsetPlacement.vertical(ConstantInt.of(verticalOffset)));
    }

    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(BootstrapContext<ConfiguredFeature<?, ?>> context, ResourceKey<ConfiguredFeature<?, ?>> key, F feature, FC config) {
        context.register(key, new ConfiguredFeature<>(feature, config));
    }

}
