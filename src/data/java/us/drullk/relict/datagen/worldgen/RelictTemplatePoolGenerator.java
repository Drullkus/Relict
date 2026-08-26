package us.drullk.relict.datagen.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import us.drullk.relict.Relict;
import us.drullk.relict.init.worldgen.RelictStructureProcessors;
import us.drullk.relict.init.worldgen.RelictTemplatePools;
import us.drullk.relict.worldgen.SinglePoolElementCustomDelta;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;

public class RelictTemplatePoolGenerator {

    private static final ResourceKey<StructureTemplatePool> EMPTY = ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace("empty"));

    /** What every wreck template's starter jigsaw targets — the sentinel {@code JigsawPlacement} anchors on. */
    private static final Identifier STRUCTURE_START = Identifier.withDefaultNamespace("structure_start");

    public static void bootstrapTemplatePools(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureTemplatePool> pools = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> empty = pools.getOrThrow(EMPTY);
        HolderGetter<StructureProcessorList> processorLists = context.lookup(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> solarPanelDecay = processorLists.getOrThrow(RelictStructureProcessors.UNMANNED_WRECK_SOLAR_PANEL_DECAY);
        HolderGetter<Block> blocks = context.lookup(Registries.BLOCK);

        context.register(RelictTemplatePools.PORTAL_RUIN_START, new StructureTemplatePool(
                empty,
                ImmutableList.of(Pair.of(StructurePoolElement.single("relict:mars_portal_ruin/frame"), 1)),
                Projection.RIGID
        ));

        context.register(RelictTemplatePools.UNMANNED_WRECK_START, new StructureTemplatePool(
                empty,
                ImmutableList.of(
                        Pair.of(wreckElement(blocks, "bib_rover", solarPanelDecay), 1),
                        Pair.of(wreckElement(blocks, "buggy_rover", solarPanelDecay), 1),
                        Pair.of(wreckElement(blocks, "disc_lander", solarPanelDecay), 1),
                        Pair.of(wreckElement(blocks, "egg_lander_3", solarPanelDecay), 1),
                        Pair.of(wreckElement(blocks, "eight_rover", solarPanelDecay), 1),
                        Pair.of(wreckElement(blocks, "overturned_rover", solarPanelDecay), 1),
                        Pair.of(wreckElement(blocks, "rtg_rover", solarPanelDecay), 1),
                        Pair.of(wreckElement(blocks, "wide_rover", solarPanelDecay), 1)
                ),
                Projection.RIGID
        ));

        context.register(RelictTemplatePools.RUIN_A_START, new StructureTemplatePool(
                empty,
                ImmutableList.of(Pair.of(StructurePoolElement.single("relict:ruin_a/start"), 1)),
                Projection.RIGID
        ));

        context.register(RelictTemplatePools.RUIN_A_MESSAGE_ROOM, new StructureTemplatePool(
                empty,
                ImmutableList.of(Pair.of(StructurePoolElement.single("relict:ruin_a/message_room"), 1)),
                Projection.RIGID
        ));

        context.register(RelictTemplatePools.RUIN_A_CORRIDORS, new StructureTemplatePool(
                empty,
                ImmutableList.of(
                        Pair.of(StructurePoolElement.single("relict:ruin_a/corridor_straight"), 1),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/corridor_turn"), 1)
                ),
                Projection.RIGID
        ));

        context.register(RelictTemplatePools.RUIN_A_ROOMS, new StructureTemplatePool(
                empty,
                ImmutableList.of(
                        Pair.of(StructurePoolElement.single("relict:ruin_a/room_a"), 1),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/room_b"), 1),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/room_c"), 1)
                ),
                Projection.RIGID
        ));

        context.register(RelictTemplatePools.RUIN_A_CAPS, new StructureTemplatePool(
                empty,
                ImmutableList.of(
                        Pair.of(StructurePoolElement.single("relict:ruin_a/cap_a"), 1),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/cap_b"), 1)
                ),
                Projection.RIGID
        ));

        context.register(RelictTemplatePools.RUIN_A_NEXT, new StructureTemplatePool(
                empty,
                ImmutableList.of(
                        Pair.of(StructurePoolElement.single("relict:ruin_a/corridor_straight"), 3),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/corridor_turn"), 3),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/room_a"), 2),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/room_b"), 2),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/room_c"), 2),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/cap_a"), 1),
                        Pair.of(StructurePoolElement.single("relict:ruin_a/cap_b"), 1)
                ),
                Projection.RIGID
        ));
    }

    /** A wreck pool element whose ground-level delta is read straight off its own template's starter jigsaw. */
    private static Function<Projection, SinglePoolElementCustomDelta> wreckElement(HolderGetter<Block> blocks, String templateName, Holder<StructureProcessorList> processors) {
        return SinglePoolElementCustomDelta.single("relict:unmanned_wreck/" + templateName, processors, groundLevelDelta(blocks, templateName));
    }

    /**
     * Distance from {@code templateName}'s starter jigsaw (the one targeting {@code minecraft:structure_start})
     * down to the template's own bounding-box bottom. A saved structure template always stores its blocks
     * relative to its own lowest corner, so that bottom is local y=0 and the delta is simply the jigsaw's
     * local y. Read straight off the authored NBT, once, at datagen time — never per chunk.
     */
    private static int groundLevelDelta(HolderGetter<Block> blocks, String templateName) {
        Identifier location = Relict.id("unmanned_wreck/" + templateName);
        String resourcePath = "/data/" + location.getNamespace() + "/structure/" + location.getPath() + ".nbt";

        CompoundTag tag;
        try (InputStream stream = RelictTemplatePoolGenerator.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing structure template on the datagen classpath: " + resourcePath);
            }
            tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        StructureTemplate template = new StructureTemplate();
        template.load(blocks, tag);

        List<BlockPos> starters = template.getJigsaws(BlockPos.ZERO, Rotation.NONE).stream()
                .filter(jigsaw -> STRUCTURE_START.equals(jigsaw.target()))
                .map(jigsaw -> jigsaw.info().pos())
                .toList();

        if (starters.size() != 1) {
            throw new IllegalStateException(templateName + ": expected exactly one jigsaw targeting "
                    + STRUCTURE_START + ", found " + starters.size());
        }

        return starters.getFirst().getY();
    }

}
