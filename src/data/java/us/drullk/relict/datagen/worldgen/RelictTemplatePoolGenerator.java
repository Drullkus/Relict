package us.drullk.relict.datagen.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection;
import us.drullk.relict.init.worldgen.RelictTemplatePools;

public class RelictTemplatePoolGenerator {

    private static final ResourceKey<StructureTemplatePool> EMPTY = ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace("empty"));

    public static void bootstrapTemplatePools(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureTemplatePool> pools = context.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> empty = pools.getOrThrow(EMPTY);

        context.register(RelictTemplatePools.PORTAL_RUIN_START, new StructureTemplatePool(
                empty,
                ImmutableList.of(Pair.of(StructurePoolElement.single("relict:mars_portal_ruin/frame"), 1)),
                Projection.RIGID
        ));

        context.register(RelictTemplatePools.UNMANNED_WRECK_START, new StructureTemplatePool(
                empty,
                ImmutableList.of(
                        Pair.of(StructurePoolElement.single("relict:unmanned_wreck/lander"), 1),
                        Pair.of(StructurePoolElement.single("relict:unmanned_wreck/rover"), 1)
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

}
