package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import us.drullk.relict.init.custom.RelictCustomRegistries;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class VoronoiBiomeSource extends BiomeSource {

    private static final Codec<Holder<VoronoiSource>> SOURCE = RegistryFixedCodec.create(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY);

    public static final MapCodec<VoronoiBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SOURCE.fieldOf("voronoi_source").forGetter(biomeSource -> biomeSource.voronoiSource),
            SOURCE.optionalFieldOf("underground_source").forGetter(biomeSource -> biomeSource.undergroundSource),
            Codec.INT.optionalFieldOf("underground_depth", 0).forGetter(biomeSource -> biomeSource.undergroundDepth)
    ).apply(instance, VoronoiBiomeSource::new));

    private final Holder<VoronoiSource> voronoiSource;
    private final Optional<Holder<VoronoiSource>> undergroundSource;
    private final int undergroundDepth;

    public VoronoiBiomeSource(final Holder<VoronoiSource> voronoiSource) {
        this(voronoiSource, Optional.empty(), 0);
    }

    public VoronoiBiomeSource(final Holder<VoronoiSource> voronoiSource, final Optional<Holder<VoronoiSource>> undergroundSource, final int undergroundDepth) {
        this.voronoiSource = voronoiSource;
        this.undergroundSource = undergroundSource;
        this.undergroundDepth = undergroundDepth;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(Stream.of(this.voronoiSource), this.undergroundSource.stream())
                .flatMap(source -> source.value().provinces().unwrap().stream())
                .map(Weighted::value)
                .map(province -> province.value().biome());
    }

    @Override
    public Holder<Biome> getNoiseBiome(final int quartX, final int quartY, final int quartZ, final Climate.Sampler sampler) {
        return this.provinceAt(QuartPos.toBlock(quartX), QuartPos.toBlock(quartY), QuartPos.toBlock(quartZ), sampler).value().biome();
    }

    @Override
    public void addDebugInfo(final List<String> result, final BlockPos feetPos, final Climate.Sampler sampler) {
        VoronoiSource source = this.sourceAt(feetPos.getX(), feetPos.getY(), feetPos.getZ(), sampler);
        VoronoiSource.Cell cell = source.nearest(feetPos.getX(), feetPos.getZ());
        Holder<Province> province = source.provinceAt(cell.cellX(), cell.cellZ());

        result.add(MessageFormat.format(
                "Province: {0} cell [{1}, {2}]{3}",
                province.unwrapKey().map(key -> key.identifier().toString()).orElse("(inline)"),
                cell.cellX(),
                cell.cellZ(),
                String.format(
                        " edge %.1f border %.1f epoch %+.2f surface %.1f cut %.1f",
                        cell.edgeDistance(),
                        cell.distanceToSecondCenter() - cell.distanceToCenter(),
                        source.cellEpoch(cell.cellX(), cell.cellZ()),
                        this.surfaceLevel(feetPos.getX(), feetPos.getZ(), sampler),
                        this.surfaceLevel(feetPos.getX(), feetPos.getZ(), sampler) - this.undergroundDepth
                )
        ));
    }

    private Holder<Province> provinceAt(final int blockX, final int blockY, final int blockZ, final Climate.Sampler sampler) {
        VoronoiSource source = this.sourceAt(blockX, blockY, blockZ, sampler);
        VoronoiSource.Cell cell = source.nearest(blockX, blockZ);
        return source.provinceAt(cell.cellX(), cell.cellZ());
    }

    private VoronoiSource sourceAt(final int blockX, final int blockY, final int blockZ, final Climate.Sampler sampler) {
        if (this.undergroundSource.isEmpty() || blockY >= this.surfaceLevel(blockX, blockZ, sampler) - this.undergroundDepth) {
            return VoronoiSource.seeded(this.voronoiSource);
        }

        return VoronoiSource.seeded(this.undergroundSource.get());
    }

    private double surfaceLevel(final int blockX, final int blockZ, final Climate.Sampler sampler) {
        return sampler.continentalness().compute(new DensityFunction.SinglePointContext(blockX, 0, blockZ));
    }

}
