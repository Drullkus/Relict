package us.drullk.relict.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link SinglePoolElement} whose ground-level delta is a fixed, per-template value carried in the
 * codec instead of the vanilla constant {@code 1} ({@link net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement#getGroundLevelDelta()}).
 * The value is the distance, in the template's own local space, from its starter jigsaw block (the one
 * targeting {@code minecraft:structure_start}) down to the template's bounding-box bottom — always local
 * y=0, since a saved structure template stores every block position relative to its own lowest corner.
 *
 * <p>Every consumer of {@code getGroundLevelDelta()} ({@code JigsawPlacement}, {@code Beardifier}) already
 * reads it off the element/piece with no other vanilla code to touch — this class only supplies a
 * template-aware value instead of the hardcoded one.
 */
public class SinglePoolElementCustomDelta extends SinglePoolElement {

    public static final MapCodec<SinglePoolElementCustomDelta> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            templateCodec(), processorsCodec(), projectionCodec(), overrideLiquidSettingsCodec(),
            Codec.INT.fieldOf("ground_level_delta").forGetter(SinglePoolElementCustomDelta::getGroundLevelDelta)
    ).apply(instance, SinglePoolElementCustomDelta::new));

    public static final StructurePoolElementType<SinglePoolElementCustomDelta> TYPE = () -> CODEC;

    private final int groundLevelDelta;

    protected SinglePoolElementCustomDelta(Either<Identifier, StructureTemplate> template, Holder<StructureProcessorList> processors,
                                           Projection projection, Optional<LiquidSettings> overrideLiquidSettings, int groundLevelDelta) {
        super(template, processors, projection, overrideLiquidSettings);
        this.groundLevelDelta = groundLevelDelta;
    }

    public static Function<Projection, SinglePoolElementCustomDelta> single(String location, Holder<StructureProcessorList> processors, int groundLevelDelta) {
        return projection -> new SinglePoolElementCustomDelta(Either.left(Identifier.parse(location)), processors, projection, Optional.empty(), groundLevelDelta);
    }

    @Override
    public int getGroundLevelDelta() {
        return this.groundLevelDelta;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "Wreck[" + this.template + "]";
    }

}
