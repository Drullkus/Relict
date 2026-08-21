package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import us.drullk.relict.RelictTags;
import us.drullk.relict.init.worldgen.RelictStructures;
import us.drullk.relict.init.worldgen.RelictTemplatePools;

public class RelictStructureGenerator {

    public static void bootstrapStructures(BootstrapContext<Structure> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        HolderGetter<StructureTemplatePool> pools = context.lookup(Registries.TEMPLATE_POOL);

        context.register(RelictStructures.MARS_PORTAL, new JigsawStructure(
                new Structure.StructureSettings.Builder(biomes.getOrThrow(RelictTags.HAS_STRUCTURE_PORTAL_RUIN))
                        .generationStep(GenerationStep.Decoration.UNDERGROUND_STRUCTURES)
                        .terrainAdapation(TerrainAdjustment.BEARD_BOX)
                        .build(),
                pools.getOrThrow(RelictTemplatePools.PORTAL_RUIN_START),
                1,
                UniformHeight.of(VerticalAnchor.absolute(-40), VerticalAnchor.absolute(60)),
                false
        ));

        context.register(RelictStructures.UNMANNED_WRECK, new JigsawStructure(
                new Structure.StructureSettings.Builder(biomes.getOrThrow(RelictTags.HAS_STRUCTURE_UNMANNED_WRECK))
                        .terrainAdapation(TerrainAdjustment.BEARD_THIN)
                        .build(),
                pools.getOrThrow(RelictTemplatePools.UNMANNED_WRECK_START),
                1,
                ConstantHeight.ZERO,
                false,
                Heightmap.Types.WORLD_SURFACE_WG
        ));

        context.register(RelictStructures.RUIN_A, new JigsawStructure(
                new Structure.StructureSettings.Builder(biomes.getOrThrow(RelictTags.HAS_STRUCTURE_RUIN_A))
                        .generationStep(GenerationStep.Decoration.UNDERGROUND_STRUCTURES)
                        .terrainAdapation(TerrainAdjustment.BEARD_BOX)
                        .build(),
                pools.getOrThrow(RelictTemplatePools.RUIN_A_START),
                5,
                ConstantHeight.of(VerticalAnchor.absolute(20)),
                false
        ));
    }

    public static void bootstrapStructureSet(BootstrapContext<StructureSet> context) {
        HolderGetter<Structure> structures = context.lookup(Registries.STRUCTURE);

        context.register(RelictStructures.MARS_PORTAL_SET, new StructureSet(
                structures.getOrThrow(RelictStructures.MARS_PORTAL),
                new RandomSpreadStructurePlacement(16, 8, RandomSpreadType.LINEAR, 841004)
        ));

        context.register(RelictStructures.UNMANNED_WRECK_SET, new StructureSet(
                structures.getOrThrow(RelictStructures.UNMANNED_WRECK),
                new RandomSpreadStructurePlacement(20, 12, RandomSpreadType.LINEAR, 841005)
        ));

        context.register(RelictStructures.RUIN_A_SET, new StructureSet(
                structures.getOrThrow(RelictStructures.RUIN_A),
                new RandomSpreadStructurePlacement(24, 8, RandomSpreadType.LINEAR, 841006)
        ));
    }

}
