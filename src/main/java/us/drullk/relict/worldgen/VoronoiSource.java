package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import us.drullk.relict.init.custom.RelictCustomRegistries;

import java.util.List;

public final class VoronoiSource {

    public static final Codec<VoronoiSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 1 << 20).fieldOf("cell_size").forGetter(VoronoiSource::cellSize),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("jitter", 0.9F).forGetter(VoronoiSource::jitter),
            Codec.floatRange(0.0F, 1 << 20).optionalFieldOf("blend_width", 64.0F).forGetter(VoronoiSource::blendWidth),
            Codec.intRange(1, 1 << 24).optionalFieldOf("epoch_spacing", 4096).forGetter(VoronoiSource::epochSpacing),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("epoch_relief", 0.42F).forGetter(VoronoiSource::epochRelief),
            WeightedList.nonEmptyCodec(RegistryFixedCodec.create(RelictCustomRegistries.PROVINCE_REGISTRY).fieldOf("province")).fieldOf("provinces").forGetter(VoronoiSource::provinces)
    ).apply(instance, VoronoiSource::new));

    private static final int SCAN_RADIUS = 2;
    private static final int SCAN_WIDTH = 2 * SCAN_RADIUS + 1;
    private static final int CANDIDATES = SCAN_WIDTH * SCAN_WIDTH;

    private static final long CENTER_X_SALT = 0x5851F42D4C957F2DL;
    private static final long CENTER_Z_SALT = 0x14057B7EF767814FL;
    private static final long PROVINCE_SALT = 0x2545F4914F6CDD1DL;
    private static final long EPOCH_SALT = 0x9E3779B97F4A7C15L;

    private static final double[] EPOCH_OCTAVES = {1.0, 0.5, 0.25};
    private static final double EPOCH_NORMALIZER = 1.75;

    private static final double EPOCH_OCTAVE_SHIFT = 0.3660254;

    private final int cellSize;
    private final float jitter;
    private final float blendWidth;
    private final int epochSpacing;
    private final float epochRelief;
    private final WeightedList<Holder<Province>> provinces;

    private final List<Holder<Province>> provinceValues;
    private final int[] weights;

    private volatile boolean seeded;
    private long seed;

    public VoronoiSource(final int cellSize, final float jitter, final float blendWidth, final int epochSpacing,
                         final float epochRelief, final WeightedList<Holder<Province>> provinces) {
        double maximum = maxBlendWidth(cellSize, jitter);

        if (blendWidth > maximum) {
            throw new IllegalArgumentException("blend_width " + blendWidth + " exceeds " + maximum + ", the widest blend a " + cellSize + "-block cell at jitter " + jitter + " can hold inside the candidate scan. Raise cell_size or lower jitter or blend_width.");
        }

        this.cellSize = cellSize;
        this.jitter = jitter;
        this.blendWidth = blendWidth;
        this.epochSpacing = epochSpacing;
        this.epochRelief = epochRelief;
        this.provinces = provinces;

        List<Weighted<Holder<Province>>> entries = provinces.unwrap();
        this.provinceValues = entries.stream().map(Weighted::value).toList();
        this.weights = entries.stream().mapToInt(Weighted::weight).toArray();
    }

    public static double maxBlendWidth(final int cellSize, final float jitter) {
        double leaving = SCAN_RADIUS + 0.5 - jitter / 2.0;
        double nearest = Math.sqrt(2.0) / 2.0 * (1.0 + jitter);
        return 0.5 * (leaving - nearest) * cellSize;
    }

    public int cellSize() {
        return this.cellSize;
    }

    public float jitter() {
        return this.jitter;
    }

    public float blendWidth() {
        return this.blendWidth;
    }

    public int epochSpacing() {
        return this.epochSpacing;
    }

    public float epochRelief() {
        return this.epochRelief;
    }

    public WeightedList<Holder<Province>> provinces() {
        return this.provinces;
    }

    public void bindSeed(final Identifier identity) {
        this.bindSeed(identity, ServerLifecycleHooks.getCurrentServer().overworld().getSeed());
    }

    public void bindSeed(final Identifier identity, final long worldSeed) {
        if (this.seeded) {
            return;
        }

        synchronized (this) {
            if (this.seeded) {
                return;
            }

            this.seed = mix(worldSeed + mix(identity.hashCode()));
            this.seeded = true;
        }
    }

    public static VoronoiSource seeded(final Holder<VoronoiSource> holder) {
        VoronoiSource source = holder.value();

        if (!source.seeded) {
            source.bindSeed(holder.unwrapKey().orElseThrow().identifier());
        }

        return source;
    }

    public long seed() {
        if (!this.seeded) {
            throw new IllegalStateException("Voronoi source read before it was seeded; reach it through VoronoiSource.seeded(holder) so its registry ID is available.");
        }

        return this.seed;
    }

    public record Cell(int cellX, int cellZ, double distanceToCenter, double distanceToSecondCenter,
                       double edgeDistance) {}

    public Cell nearest(final int blockX, final int blockZ) {
        int gridX = Math.floorDiv(blockX, this.cellSize);
        int gridZ = Math.floorDiv(blockZ, this.cellSize);
        double[] distances = new double[CANDIDATES];
        double[] centers = this.scan(gridX, gridZ, blockX, blockZ, distances);

        int nearest = 0;
        double second = Double.MAX_VALUE;
        for (int i = 1; i < CANDIDATES; i++) {
            if (distances[i] < distances[nearest]) {
                second = distances[nearest];
                nearest = i;
            } else if (distances[i] < second) {
                second = distances[i];
            }
        }

        return new Cell(gridX + nearest % SCAN_WIDTH - SCAN_RADIUS, gridZ + nearest / SCAN_WIDTH - SCAN_RADIUS,
                distances[nearest], second, edgeDistance(centers, nearest, blockX, blockZ));
    }

    @FunctionalInterface
    public interface CellField {
        double valueAt(Province province, int cellX, int cellZ);
    }

    public double blend(final int blockX, final int blockZ, final CellField field) {
        int gridX = Math.floorDiv(blockX, this.cellSize);
        int gridZ = Math.floorDiv(blockZ, this.cellSize);
        double[] distances = new double[CANDIDATES];
        this.scan(gridX, gridZ, blockX, blockZ, distances);

        double smallest = Double.MAX_VALUE;
        for (double distance : distances) {
            smallest = Math.min(smallest, distance);
        }

        double total = 0.0;
        double sum = 0.0;

        for (int i = 0; i < CANDIDATES; i++) {
            double weight = this.kernel(distances[i] - smallest);
            if (weight <= 0.0) {
                continue;
            }

            int cellX = gridX + i % SCAN_WIDTH - SCAN_RADIUS;
            int cellZ = gridZ + i / SCAN_WIDTH - SCAN_RADIUS;
            total += weight;
            sum += weight * field.valueAt(this.provinceAt(cellX, cellZ).value(), cellX, cellZ);
        }

        return sum / total;
    }

    private double[] scan(final int gridX, final int gridZ, final int blockX, final int blockZ, final double[] distances) {
        double[] centers = new double[CANDIDATES * 2];

        for (int i = 0; i < CANDIDATES; i++) {
            int cellX = gridX + i % SCAN_WIDTH - SCAN_RADIUS;
            int cellZ = gridZ + i / SCAN_WIDTH - SCAN_RADIUS;
            double centerX = this.center(cellX, cellZ, cellX, CENTER_X_SALT);
            double centerZ = this.center(cellX, cellZ, cellZ, CENTER_Z_SALT);
            centers[i * 2] = centerX;
            centers[i * 2 + 1] = centerZ;
            distances[i] = Math.sqrt((centerX - blockX) * (centerX - blockX) + (centerZ - blockZ) * (centerZ - blockZ));
        }

        return centers;
    }

    private double kernel(final double surplus) {
        if (this.blendWidth <= 0.0F) {
            return surplus <= 0.0 ? 1.0 : 0.0;
        }

        double falloff = 1.0 - surplus / (2.0 * this.blendWidth);
        return falloff <= 0.0 ? 0.0 : falloff * falloff;
    }

    private static double edgeDistance(final double[] centers, final int nearest, final int blockX, final int blockZ) {
        double centerX = centers[nearest * 2];
        double centerZ = centers[nearest * 2 + 1];
        double edge = Double.MAX_VALUE;

        for (int i = 0; i < CANDIDATES; i++) {
            if (i == nearest) {
                continue;
            }

            double towardsX = centers[i * 2] - centerX;
            double towardsZ = centers[i * 2 + 1] - centerZ;
            double length = Math.sqrt(towardsX * towardsX + towardsZ * towardsZ);
            if (length == 0.0) {
                continue;
            }

            double midpointX = centerX + towardsX * 0.5;
            double midpointZ = centerZ + towardsZ * 0.5;
            edge = Math.min(edge, ((midpointX - blockX) * towardsX + (midpointZ - blockZ) * towardsZ) / length);
        }

        return Math.max(edge, 0.0);
    }

    public double centerX(final int cellX, final int cellZ) {
        return this.center(cellX, cellZ, cellX, CENTER_X_SALT);
    }

    public double centerZ(final int cellX, final int cellZ) {
        return this.center(cellX, cellZ, cellZ, CENTER_Z_SALT);
    }

    private double center(final int cellX, final int cellZ, final int axis, final long salt) {
        return (axis + 0.5 + this.jitter * (unitInterval(hash(this.seed(), cellX, cellZ, salt)) - 0.5)) * this.cellSize;
    }

    public double epochAt(final double worldX, final double worldZ) {
        double value = 0.0;
        double frequency = 1.0 / this.epochSpacing;

        for (int octave = 0; octave < EPOCH_OCTAVES.length; octave++) {
            double shift = octave * EPOCH_OCTAVE_SHIFT;
            value += EPOCH_OCTAVES[octave] * this.latticeNoise(worldX * frequency + shift, worldZ * frequency + shift, octave);
            frequency *= 2.0;
        }

        return value / EPOCH_NORMALIZER;
    }

    public double cellEpoch(final int cellX, final int cellZ) {
        return this.epochAt(this.centerX(cellX, cellZ), this.centerZ(cellX, cellZ));
    }

    public double cellElevation(final Province province, final int cellX, final int cellZ) {
        return Mth.clamp(province.elevationOffset() + this.epochRelief * this.cellEpoch(cellX, cellZ), -1.0, 1.0);
    }

    private double latticeNoise(final double x, final double z, final int octave) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        double fadeX = fade(x - x0);
        double fadeZ = fade(z - z0);

        double low = Mth.lerp(fadeX, this.latticeValue(x0, z0, octave), this.latticeValue(x0 + 1, z0, octave));
        double high = Mth.lerp(fadeX, this.latticeValue(x0, z0 + 1, octave), this.latticeValue(x0 + 1, z0 + 1, octave));
        return Mth.lerp(fadeZ, low, high);
    }

    private double latticeValue(final int x, final int z, final int octave) {
        return unitInterval(hash(this.seed() + octave, x, z, EPOCH_SALT)) * 2.0 - 1.0;
    }

    /** Perlin's quintic fade, so the field is C2 and the lattice never shows as a crease. */
    private static double fade(final double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    public Holder<Province> provinceAt(final int cellX, final int cellZ) {
        double epoch = this.cellEpoch(cellX, cellZ);
        double total = 0.0;

        for (int i = 0; i < this.weights.length; i++) {
            total += this.weights[i] * this.provinceValues.get(i).value().elevationClass().affinity(epoch);
        }

        double roll = unitInterval(hash(this.seed(), cellX, cellZ, PROVINCE_SALT)) * total;
        double running = 0.0;

        for (int i = 0; i < this.weights.length; i++) {
            running += this.weights[i] * this.provinceValues.get(i).value().elevationClass().affinity(epoch);
            if (roll < running) {
                return this.provinceValues.get(i);
            }
        }

        return this.provinceValues.getLast();
    }

    private static long hash(final long seed, final int cellX, final int cellZ, final long salt) {
        return mix(mix(mix(seed + salt) ^ cellX * 0x9E3779B97F4A7C15L) ^ cellZ * 0xC2B2AE3D27D4EB4FL);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;
        return value;
    }

    private static double unitInterval(final long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

}
