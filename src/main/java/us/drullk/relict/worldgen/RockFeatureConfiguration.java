package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Per-placement tuning for {@link RockFeature}: one configurable rock family shared by every
 * biome's clasts, boulders and lone blocks rather than a feature per biome per size (per the brief's
 * "Shared implementation shape" law). Values live in datapack JSON, registered from
 * {@code RockFeatureGenerator} the same way {@link DustLayerFeatureConfiguration} is.
 *
 * <p>{@code province} exists for the same reason {@link DustLayerFeatureConfiguration#province()} does: a
 * chunk straddling a province border still runs every feature its one representative biome sample lists,
 * so the feature re-checks the real biome at each column it actually touches (the border-bleed lesson).
 *
 * <p>{@code secondaryBlock}/{@code secondaryChance} is the whole "variety mix" knob: every individual voxel
 * a placement writes rolls independently, {@code secondaryBlock} with probability {@code secondaryChance}
 * and {@code primaryBlock} otherwise — 0.0 for a single-block-type placement (most of them), non-zero for
 * the wrinkle_plains L ejecta boulder's majority-basalt/minority-tuff mix (mottled across the one boulder,
 * which is the intended read) and the fretted_mesas floor scatter's terracotta/tuff mix. A single-voxel
 * {@code SINGLE} placement only ever rolls once, so the two roll shapes agree there.
 */
public record RockFeatureConfiguration(
        ResourceKey<Biome> province,
        RockShape shape,
        Block primaryBlock,
        Block secondaryBlock,
        float secondaryChance,
        PlacementRule placementRule
) implements FeatureConfiguration {

    public static final Codec<RockFeatureConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.BIOME).fieldOf("province").forGetter(RockFeatureConfiguration::province),
            RockShape.CODEC.fieldOf("shape").forGetter(RockFeatureConfiguration::shape),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("primary_block").forGetter(RockFeatureConfiguration::primaryBlock),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("secondary_block").forGetter(RockFeatureConfiguration::secondaryBlock),
            Codec.floatRange(0.0F, 1.0F).fieldOf("secondary_chance").forGetter(RockFeatureConfiguration::secondaryChance),
            PlacementRule.CODEC.fieldOf("placement_rule").forGetter(RockFeatureConfiguration::placementRule)
    ).apply(instance, RockFeatureConfiguration::new));

    /** S/M/L per the brief: a single block, a small 2-4 block clast, or a rare rounded boulder. */
    public enum RockShape implements StringRepresentable {
        SINGLE("single"),
        CLAST("clast"),
        BOULDER("boulder");

        public static final Codec<RockShape> CODEC = StringRepresentable.fromEnum(RockShape::values);

        private final String name;

        RockShape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /**
     * The column predicate gating where a placement attempt is allowed to land, on top of the biome check
     * every attempt already gets from {@code BiomeFilter.biome()}. Each case reads {@link RockRelief}
     * and/or {@link DuneCrest} — see {@link RockFeature} for the thresholds.
     */
    public enum PlacementRule implements StringRepresentable {
        /** No positional gate — sparse-even coverage (wrinkle_plains S rocks). */
        ANY("any"),
        /** Not a dune crest, and locally flat — the interdune corridor (rusted_dunes S ventifacts). */
        INTERDUNE_FLOOR("interdune_floor"),
        /** Locally high relief — biased onto/near a wrinkle-ridge scarp (wrinkle_plains M ridge rubble). */
        RIDGE_BIAS("ridge_bias"),
        /** Locally high relief — near a mesa cliff foot (fretted_mesas S+M talus). */
        TALUS("talus"),
        /** Locally flat, at or above the cap-height threshold (fretted_mesas rare S cap-top blocks). */
        CAP("cap"),
        /** Locally flat, below the cap-height threshold — the tableland floor (fretted_mesas S floor scatter). */
        VALLEY_FLOOR("valley_floor");

        public static final Codec<PlacementRule> CODEC = StringRepresentable.fromEnum(PlacementRule::values);

        private final String name;

        PlacementRule(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

}
