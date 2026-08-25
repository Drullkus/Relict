package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Per-province tuning for {@link DustLayerFeature}: how deep the baseline veneer runs, how patchy its
 * coverage reads, and whether it is further confined to dune crests. Values live in datapack JSON (codec'd,
 * not a Java constant) because they are province data, the same way {@code Province}'s own relief fields are
 * — see {@code RelictProvinceGenerator} for the registration-time constants that produce these.
 *
 * <p>{@code province} exists because biome decoration is chunk-granular, not column-granular: a chunk that
 * straddles a province border still runs every feature the chunk's one representative biome sample lists,
 * for the whole chunk. [VANILLACOPY, pattern] vanilla's own {@code SnowAndFreezeFeature} answers this the
 * same way — it re-reads the real biome at each of the 256 columns it walks rather than trusting registration
 * membership — so a dust patch that starts in one province and a chunk corner that belongs to its neighbour
 * never gets the wrong province's depth table.
 */
public record DustLayerFeatureConfiguration(ResourceKey<Biome> province, int minLayers, int maxLayers, double coverageChance, int patchCellSize,
        boolean requireDuneCrest, long coverageSalt) implements FeatureConfiguration {

    public static final Codec<DustLayerFeatureConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.BIOME).fieldOf("province").forGetter(DustLayerFeatureConfiguration::province),
            Codec.intRange(0, 8).fieldOf("min_layers").forGetter(DustLayerFeatureConfiguration::minLayers),
            Codec.intRange(1, 8).fieldOf("max_layers").forGetter(DustLayerFeatureConfiguration::maxLayers),
            Codec.doubleRange(0.0, 1.0).fieldOf("coverage_chance").forGetter(DustLayerFeatureConfiguration::coverageChance),
            Codec.intRange(1, 64).fieldOf("patch_cell_size").forGetter(DustLayerFeatureConfiguration::patchCellSize),
            Codec.BOOL.fieldOf("require_dune_crest").forGetter(DustLayerFeatureConfiguration::requireDuneCrest),
            Codec.LONG.fieldOf("coverage_salt").forGetter(DustLayerFeatureConfiguration::coverageSalt)
    ).apply(instance, DustLayerFeatureConfiguration::new));

}
