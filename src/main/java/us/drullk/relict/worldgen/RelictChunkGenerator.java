package us.drullk.relict.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

import java.util.OptionalInt;

/**
 * The Mars generator. Identical to {@link NoiseBasedChunkGenerator} except that a height probe walks only
 * the part of the column that can hold the surface.
 *
 * <h2>Why the probe is worth overriding</h2>
 * A vanilla probe builds a one-cell {@code NoiseChunk} over the whole 384-block column and eagerly fills
 * both interpolation slices, which evaluates the cave/density graph 196 times before the walk reads its
 * first block — the same 196 evaluations whether the surface is at y 320 or y 100. Structure placement
 * makes several probes per candidate chunk, so a ring scan pays that column tax tens of thousands of times.
 *
 * <h2>Why the shorter walk answers the same</h2>
 * Terrain density is {@code 0.1 * (preliminarySurfaceLevel + 0.5 - y)}, and above that surface the cave
 * route can only lower it further, so every cell corner over a column's own preliminary surface is negative.
 * A block is solid only if a corner of its own cell is positive, and a cell reaches one cell height up, so
 * nothing can be solid more than one cell above the highest preliminary surface among the four corners the
 * probe's cell interpolates between. {@link #PROBE_HEADROOM} covers that reach; a probe that finds no
 * surface inside its window then walks what is under the window, so the answer is vanilla's in every case.
 */
public class RelictChunkGenerator extends NoiseBasedChunkGenerator {

    public static final MapCodec<RelictChunkGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
    ).apply(instance, instance.stable(RelictChunkGenerator::new)));

    /** Two cells above the cell's highest corner surface, where one cell is already out of interpolation reach. */
    public static final int PROBE_HEADROOM = 16;

    /** How far under the preliminary surface a probe looks before it drops to a second, lower walk. */
    public static final int PROBE_DEPTH = 64;

    public RelictChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MAP_CODEC;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        NoiseSettings settings = this.generatorSettings().value().noiseSettings().clampToHeightAccessor(heightAccessor);
        LevelHeightAccessor window = probeWindow(x, z, settings, randomState);

        if (window == null) {
            return super.getBaseHeight(x, z, type, heightAccessor, randomState);
        }

        OptionalInt found = this.iterateNoiseColumn(window, randomState, x, z, null, type.isOpaque());

        if (found.isPresent()) {
            return found.getAsInt();
        }

        // Nothing above the window and nothing in it, so the surface can only be under it. A cave mouth
        // deeper than the window puts a column here.
        int belowHeight = window.getMinY() - settings.minY();

        if (belowHeight < settings.getCellHeight()) {
            return heightAccessor.getMinY();
        }

        LevelHeightAccessor below = LevelHeightAccessor.create(settings.minY(), belowHeight);
        return this.iterateNoiseColumn(below, randomState, x, z, null, type.isOpaque()).orElse(heightAccessor.getMinY());
    }

    /** @return the cell-aligned slice of the column a probe has to walk, or null if that is the whole column. */
    private static @Nullable LevelHeightAccessor probeWindow(int x, int z, NoiseSettings settings, RandomState randomState) {
        int cellWidth = settings.getCellWidth();
        int cellHeight = settings.getCellHeight();
        DensityFunction surface = randomState.router().preliminarySurfaceLevel();

        int cornerX = Math.floorDiv(x, cellWidth) * cellWidth;
        int cornerZ = Math.floorDiv(z, cellWidth) * cellWidth;
        double highest = Math.max(
                Math.max(surfaceAt(surface, cornerX, cornerZ), surfaceAt(surface, cornerX + cellWidth, cornerZ)),
                Math.max(surfaceAt(surface, cornerX, cornerZ + cellWidth), surfaceAt(surface, cornerX + cellWidth, cornerZ + cellWidth)));

        int columnBottom = settings.minY();
        int columnTop = columnBottom + settings.height();
        int top = Math.min(ceilToCell(Mth.floor(highest) + PROBE_HEADROOM, cellHeight), columnTop);
        int bottom = Math.max(floorToCell(Mth.floor(highest) - PROBE_DEPTH, cellHeight), columnBottom);

        return top - bottom < cellHeight || top - bottom >= settings.height() ? null : LevelHeightAccessor.create(bottom, top - bottom);
    }

    private static double surfaceAt(DensityFunction surface, int blockX, int blockZ) {
        return surface.compute(new DensityFunction.SinglePointContext(blockX, 0, blockZ));
    }

    private static int floorToCell(int y, int cellHeight) {
        return Math.floorDiv(y, cellHeight) * cellHeight;
    }

    private static int ceilToCell(int y, int cellHeight) {
        return -Math.floorDiv(-y, cellHeight) * cellHeight;
    }

}
