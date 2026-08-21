package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import us.drullk.relict.Relict;
import us.drullk.relict.init.worldgen.RelictBiomes;
import us.drullk.relict.worldgen.DuneCrestCondition;

public class RelictSurfaceRules {

    private static final SurfaceRules.RuleSource BEDROCK = state(Blocks.BEDROCK);
    private static final SurfaceRules.RuleSource RED_SAND = state(Blocks.RED_SAND);
    private static final SurfaceRules.RuleSource RED_SANDSTONE = state(Blocks.RED_SANDSTONE);
    private static final SurfaceRules.RuleSource SMOOTH_BASALT = state(Blocks.SMOOTH_BASALT);
    private static final SurfaceRules.RuleSource TERRACOTTA = state(Blocks.TERRACOTTA);
    private static final SurfaceRules.RuleSource TUFF = state(Blocks.TUFF);
    private static final SurfaceRules.RuleSource GRAVEL = state(Blocks.GRAVEL);

    private static final SurfaceRules.RuleSource SULFUR = state(Blocks.SULFUR);
    private static final SurfaceRules.RuleSource CINNABAR = state(Blocks.CINNABAR);
    private static final SurfaceRules.RuleSource CALCITE = state(Blocks.CALCITE);
    private static final SurfaceRules.RuleSource DRIPSTONE_BLOCK = state(Blocks.DRIPSTONE_BLOCK);
    private static final SurfaceRules.RuleSource PACKED_ICE = state(Blocks.PACKED_ICE);
    private static final SurfaceRules.RuleSource BLUE_ICE = state(Blocks.BLUE_ICE);

    private static final int BEDROCK_FLOOR_DEPTH = 5;

    /** Where wind-blown dust has collected on an otherwise bare crust. */
    private static final double DUST_CATCH = 0.15;

    /**
     * The wrinkle-plains gravel/duricrust patch band, mirroring the width {@code rusted_dunes}' old cut
     * patch used. Public so the report tooling can render the identical band instead of a second copy
     * that could drift from what actually ships.
     */
    public static final double PAVEMENT_LO = -0.55;
    public static final double PAVEMENT_HI = -0.35;

    public SurfaceRules.RuleSource composeSurface(BootstrapContext<NoiseGeneratorSettings> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        SurfaceRules.ConditionSource bedrockFloor = SurfaceRules.verticalGradient(
                Relict.id("bedrock_floor").toString(),
                VerticalAnchor.bottom(),
                VerticalAnchor.aboveBottom(BEDROCK_FLOOR_DEPTH));

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(bedrockFloor, BEDROCK),
                SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes, RelictBiomes.WRINKLE_PLAINS), wrinklePlains()),
                        SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes, RelictBiomes.RUSTED_DUNES), rustedDunes()),
                        SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes, RelictBiomes.FRETTED_MESAS), frettedMesas())
                )),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes, RelictBiomes.SULFUR_CAVES), sulfurCaveBands()),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes, RelictBiomes.CALCITE_CAVES), calciteCaveBands()),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes, RelictBiomes.ICE_CAVES), iceCaveBands())
        );
    }

    /** Mirrors vanilla 26.2's sulfur_caves ground banding (report §9), reusing its own registered noise field. */
    private static SurfaceRules.RuleSource sulfurCaveBands() {
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition3d(Noises.SULFUR_CAVE_GRADIENT, -0.4, -0.1), CINNABAR),
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition3d(Noises.SULFUR_CAVE_GRADIENT, 0.0, 0.4), SULFUR),
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition3d(Noises.SULFUR_CAVE_GRADIENT, 0.4), CINNABAR)
        );
    }

    /** Calcite as the bulk accent (report §1.4), with a thin dripstone_block vein reusing vanilla's gravel-band noise. */
    private static SurfaceRules.RuleSource calciteCaveBands() {
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition3d(Noises.GRAVEL, -0.05, 0.05), DRIPSTONE_BLOCK),
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition3d(Noises.CALCITE, -0.35, 0.35), CALCITE)
        );
    }

    /** Bulk packed ice and rare blue-ice cores, reusing vanilla's frozen-peaks noise fields. */
    private static SurfaceRules.RuleSource iceCaveBands() {
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition3d(Noises.ICE, 0.55), BLUE_ICE),
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition3d(Noises.PACKED_ICE, -0.15, 0.5), PACKED_ICE)
        );
    }

    private static SurfaceRules.RuleSource wrinklePlains() {
        SurfaceRules.RuleSource sandOrSandstoneIfCeiling = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, RED_SANDSTONE),
                RED_SAND);

        SurfaceRules.RuleSource floor = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition2d(Noises.SURFACE, PAVEMENT_LO, PAVEMENT_HI), GRAVEL),
                sandOrSandstoneIfCeiling);

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, floor),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, sandOrSandstoneIfCeiling),
                SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, RED_SANDSTONE));
    }

    /**
     * Dark {@code smooth_basalt} dune bodies with {@code red_sand} confined to the crests. Crest selection
     * keys off the dune wave field's own local-maximum shape along its wind axis ({@link DuneCrestCondition})
     * rather than slope — {@code steep()} cannot fire on a dune slip face
     */
    private static SurfaceRules.RuleSource rustedDunes() {
        SurfaceRules.RuleSource crestOrBody = SurfaceRules.sequence(
                SurfaceRules.ifTrue(DuneCrestCondition.INSTANCE, RED_SAND),
                SMOOTH_BASALT);

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, crestOrBody),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SMOOTH_BASALT),
                SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, SMOOTH_BASALT));
    }

    /**
     * Baked crust on the caps and floors, brecciated rock on the scarps.
     * <p>
     * Here {@code steep()} does fire: the prototyped cliffs stand at 78-85 degrees, well past its 63-degree
     * threshold, so the cornered cliff faces read as a different rock from the tablelands they cut. {@code tuff}
     * also does not fall, which matters on a face that drops 20 blocks in six.
     */
    private static SurfaceRules.RuleSource frettedMesas() {
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.steep(), TUFF),
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition2d(Noises.SURFACE, DUST_CATCH), RED_SAND),
                        TERRACOTTA)),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, TUFF),
                SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, TUFF));
    }

    private static SurfaceRules.RuleSource state(Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }

}
