package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.TrapezoidFloat;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarverDebugSettings;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import us.drullk.relict.init.worldgen.RelictCarvers;

public class RelictCarverGenerator {

    private static final VerticalAnchor NO_CARVER_LAVA = VerticalAnchor.aboveBottom(-256);

    // [VANILLACOPY] net.minecraft.data.worldgen.Carvers, with lava_level moved below the world floor.
    public static void bootstrapCarvers(BootstrapContext<ConfiguredWorldCarver<?>> context) {
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);
        HolderSet<Block> replaceable = blocks.getOrThrow(BlockTags.OVERWORLD_CARVER_REPLACEABLES);

        context.register(RelictCarvers.CAVE, WorldCarver.CAVE.configured(new CaveCarverConfiguration(
                0.15F,
                UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)),
                UniformFloat.of(0.1F, 0.9F),
                NO_CARVER_LAVA,
                replaceable,
                UniformFloat.of(0.7F, 1.4F),
                UniformFloat.of(0.8F, 1.3F),
                UniformFloat.of(-1.0F, -0.4F)
        )));

        context.register(RelictCarvers.CAVE_EXTRA_UNDERGROUND, WorldCarver.CAVE.configured(new CaveCarverConfiguration(
                0.07F,
                UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(47)),
                UniformFloat.of(0.1F, 0.9F),
                NO_CARVER_LAVA,
                replaceable,
                UniformFloat.of(0.7F, 1.4F),
                UniformFloat.of(0.8F, 1.3F),
                UniformFloat.of(-1.0F, -0.4F)
        )));

        context.register(RelictCarvers.CANYON, WorldCarver.CANYON.configured(new CanyonCarverConfiguration(
                0.01F,
                UniformHeight.of(VerticalAnchor.absolute(10), VerticalAnchor.absolute(67)),
                ConstantFloat.of(3.0F),
                NO_CARVER_LAVA,
                CarverDebugSettings.DEFAULT,
                replaceable,
                UniformFloat.of(-0.125F, 0.125F),
                new CanyonCarverConfiguration.CanyonShapeConfiguration(
                        UniformFloat.of(0.75F, 1.0F),
                        TrapezoidFloat.of(0.0F, 6.0F, 2.0F),
                        3,
                        UniformFloat.of(0.75F, 1.0F),
                        1.0F,
                        0.0F
                )
        )));
    }

}
