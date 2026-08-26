package us.drullk.relict.datagen.worldgen;

import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.worldgen.RelictStructureProcessors;

import java.util.ArrayList;
import java.util.List;

public class RelictStructureProcessorGenerator {

    private static final List<Block> DECAY_STAGES = List.of(RelictBlocks.SOLAR_PANEL_SPRINKLED.get(), RelictBlocks.SOLAR_PANEL_DUSTED.get(), RelictBlocks.SOLAR_PANEL_SANDED.get());

    public static void bootstrapProcessorLists(BootstrapContext<StructureProcessorList> context) {
        context.register(RelictStructureProcessors.UNMANNED_WRECK_SOLAR_PANEL_DECAY,
                new StructureProcessorList(List.of(new RuleProcessor(solarPanelDecayRules()))));
    }

    private static List<ProcessorRule> solarPanelDecayRules() {
        List<ProcessorRule> rules = new ArrayList<>();

        for (BlockState inputState : RelictBlocks.SOLAR_PANEL.get().getStateDefinition().getPossibleStates()) {
            for (int stage = 0; stage < DECAY_STAGES.size(); stage++) {
                float probability = 1.0F / (DECAY_STAGES.size() - stage);
                BlockState outputState = carryOverProperties(inputState, DECAY_STAGES.get(stage).defaultBlockState());
                rules.add(new ProcessorRule(new RandomBlockStateMatchTest(inputState, probability), AlwaysTrueTest.INSTANCE, outputState));
            }
        }

        return rules;
    }

    private static BlockState carryOverProperties(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = carryOverProperty(to, from, property);
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState carryOverProperty(BlockState to, BlockState from, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

}
