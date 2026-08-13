package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import us.drullk.relict.Relict;
import us.drullk.relict.init.worldgen.RelictBiomes;

public class RelictSurfaceRules {

    private static final SurfaceRules.RuleSource BEDROCK = state(Blocks.BEDROCK);
    private static final SurfaceRules.RuleSource RED_SAND = state(Blocks.RED_SAND);
    private static final SurfaceRules.RuleSource RED_SANDSTONE = state(Blocks.RED_SANDSTONE);

    private static final int BEDROCK_FLOOR_DEPTH = 5;

    public SurfaceRules.RuleSource composeSurface(BootstrapContext<NoiseGeneratorSettings> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        SurfaceRules.ConditionSource bedrockFloor = SurfaceRules.verticalGradient(
                Relict.id("bedrock_floor").toString(),
                VerticalAnchor.bottom(),
                VerticalAnchor.aboveBottom(BEDROCK_FLOOR_DEPTH));

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(bedrockFloor, BEDROCK),
                SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes, RelictBiomes.WRINKLE_PLAINS), wrinklePlains())
                )));
    }

    private static SurfaceRules.RuleSource wrinklePlains() {
        SurfaceRules.RuleSource sandOrSandstoneIfCeiling = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, RED_SANDSTONE),
                RED_SAND);

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, sandOrSandstoneIfCeiling),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, sandOrSandstoneIfCeiling),
                SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, RED_SANDSTONE));
    }

    private static SurfaceRules.RuleSource state(Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }

}
