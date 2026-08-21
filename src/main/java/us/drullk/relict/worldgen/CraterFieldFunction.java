package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import us.drullk.relict.init.custom.RelictCustomRegistries;

import java.util.List;

/**
 * The ambient crater field: an impact record laid over whatever landform a province builds.
 *
 * <h2>The field</h2>
 * Three jittered-cell layers cover the 16-256 block diameter band. Every cell rolls presence, center,
 * diameter and age from hashes alone, so the field is a pure function of position and seed with no
 * iteration over neighbors-of-neighbors. Each layer's cell edge is at least {@code FOOTPRINT} times its
 * largest radius, which is what makes a 3x3 scan per layer <em>exact</em>: a center two cells away can
 * never reach the column.
 *
 * <h2>Epoch coupling</h2>
 * Presence probability and the age mix are keyed to the epoch scalar sampled at the crater's <em>own</em>
 * center, never at the observing column. That is what keeps a crater a coherent circle where the epoch
 * varies in space, instead of a shape that changes as the epoch field crosses it. Density is convex in
 * oldness so the three chronological steps read apart rather than blurring into one saturated end.
 *
 * <h2>Two outputs, one gather</h2>
 * {@link Mode#DELTA} is the additive bowl and rim in blocks. {@link Mode#DAMP} multiplicatively suppresses
 * the host relief channels inside the cavities of <em>fresh</em> craters only: an impact resets the surface
 * it hits, but wrinkle ridges postdate most old craters, so letting a degraded cavity damp would flatten a
 * province's ridges wholesale. This one pair is why the field composes over any province primitive without
 * the primitives knowing craters exist.
 *
 * <h2>Overlap</h2>
 * Younger truncates older through an order-independent product over per-crater interior masks, so no
 * sorting is needed and the result does not depend on gather order. Ties in age are broken by a hash
 * epsilon.
 */
public final class CraterFieldFunction implements DensityFunction.SimpleFunction {

    public static final MapCodec<CraterFieldFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryFixedCodec.create(RelictCustomRegistries.VORONOI_SOURCE_REGISTRY).fieldOf("voronoi_source").forGetter(CraterFieldFunction::voronoiSource),
            Mode.CODEC.fieldOf("mode").forGetter(CraterFieldFunction::mode)
    ).apply(instance, CraterFieldFunction::new));

    private static final KeyDispatchDataCodec<CraterFieldFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    public enum Mode implements StringRepresentable {
        DELTA("delta"),
        DAMP("damp");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String name;

        Mode(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /** Footprint radius in units of the rim radius, and a hard cutoff: past this a crater contributes zero. */
    public static final double FOOTPRINT = 1.7;

    /** Truncated-Pareto exponent for diameter. This is what puts the size-frequency slope near -2. */
    public static final double PARETO_EXPONENT = 2.0;

    /** Center wander inside the cell, in units of the cell edge. */
    public static final double JITTER = 0.95;

    /**
     * Presence is convex in oldness. Real chronology piles most craters onto the oldest surfaces, and with a
     * linear mix the middle epoch rendered indistinguishable from the oldest.
     */
    public static final double DENSITY_GAMMA = 2.0;

    /**
     * Block-compressed depth law, {@code depth = DEPTH_COEFFICIENT * D^DEPTH_EXPONENT}. Real fresh simple
     * craters run about a fifth of their diameter deep, which at 256 blocks across is an untraversable 51
     * blocks; this fit passes through 16 blocks across at 3.2 deep and 256 across at 24 deep.
     */
    public static final double DEPTH_COEFFICIENT = 0.426;
    public static final double DEPTH_EXPONENT = 0.727;

    /** Fresh rim height as a share of fresh depth. */
    public static final double RIM_RATIO = 0.22;

    /** Bowl exponent: high is a flatter floor with a steeper wall, low is a shallow dish. */
    public static final double BOWL_POWER_FRESH = 3.2;
    public static final double BOWL_POWER_OLD = 1.5;

    /** Outer rim flank decay exponent; the degraded apron spreads wider. */
    public static final double RIM_FALLOFF_FRESH = 3.0;
    public static final double RIM_FALLOFF_OLD = 1.4;

    /**
     * Share of depth and rim removed at full degradation. A ghost keeps 22% of its depth on purpose: at 12%
     * a saturated old surface measured as crater-covered but read to the eye as sparse droplets, because the
     * degraded majority had gone flat.
     */
    public static final double DEPTH_LOSS = 0.78;
    public static final double RIM_LOSS = 0.85;

    /** Age mix versus epoch: young ground retains only young craters, old ground carries the full mix. */
    public static final double RETENTION_YOUNG = 0.55;
    public static final double AGE_SKEW_YOUNG = 1.6;
    public static final double AGE_SKEW_OLD = 0.75;

    /** Interior mask: full inside, faded out by the outer bound. Degraded craters truncate older ones less. */
    public static final double MASK_INNER = 0.90;
    public static final double MASK_OUTER = 1.25;
    public static final double MASK_AGE_LOSS = 0.5;

    /** Host-relief suppression inside a cavity, and the age past which a cavity no longer suppresses at all. */
    public static final double DAMP_STRENGTH = 0.85;
    public static final double DAMP_AGE_MAX = 0.4;

    private static final double DAMP_INNER = 0.80;
    private static final double DAMP_OUTER = 1.05;

    /** Rim raggedness in units of the rim radius, scaled by age: fresh rims are near-circular. */
    public static final double WOBBLE_AMPLITUDE = 0.10;

    private static final double WOBBLE_SPACING = 0.55;
    private static final double WOBBLE_PHASE_SCALE = 61.0;

    /** Ceiling on craters covering one column. Measured worst case is 7, so this is headroom, not a budget. */
    public static final int MAX_COVERING = 12;

    /** One diameter band and how often it lands, keyed to the two ends of the epoch scale. */
    public record Layer(double cell, double minDiameter, double maxDiameter,
                        double presenceYoung, double presenceOld, long salt) {}

    public static final List<Layer> LAYERS = List.of(
            new Layer(48.0, 16.0, 48.0, 0.10, 0.92, 0x9E3779B97F4A7C15L),
            new Layer(120.0, 40.0, 120.0, 0.06, 0.85, 0xC2B2AE3D27D4EB4FL),
            new Layer(224.0, 96.0, 256.0, 0.03, 0.75, 0x165667B19E3779F9L));

    static {
        for (Layer layer : LAYERS) {
            if (FOOTPRINT * layer.maxDiameter() / 2.0 > layer.cell()) {
                throw new IllegalStateException("Crater layer at cell " + layer.cell()
                        + " admits a footprint of " + FOOTPRINT * layer.maxDiameter() / 2.0
                        + " blocks, so a 3x3 cell scan would miss craters that reach the column.");
            }
        }
    }

    private static final long CRATER_SALT = 0x2545F4914F6CDD1DL;

    private static final long PRESENCE_SALT = 0x14057B7EF767814FL;
    private static final long CENTER_X_SALT = 0x5851F42D4C957F2DL;
    private static final long CENTER_Z_SALT = 0x27D4EB2F165667C5L;
    private static final long DIAMETER_SALT = 0x2545F4914F6CDD1DL;
    private static final long AGE_SALT = 0x9E3779B97F4A7C15L;
    private static final long WOBBLE_PHASE_SALT = 0xFF51AFD7ED558CCDL;
    private static final long WOBBLE_LATTICE_SALT = 0xC4CEB9FE1A85EC53L;

    /** Direct-mapped, a power of two, and sized like the voronoi site cache: adjacent columns reuse cells. */
    private static final int CELL_CACHE_SIZE = 1 << 13;
    private static final int CELL_CACHE_MASK = CELL_CACHE_SIZE - 1;

    /** One rolled crater. Everything here is a function of its cell alone, never of the observing column. */
    public record Crater(double centerX, double centerZ, double diameter, double degradation,
                         double ageKey, double wobblePhase) {}

    private record CachedCell(int layer, int cellX, int cellZ, Crater crater) {}

    private final Holder<VoronoiSource> voronoiSource;
    private final Mode mode;

    /**
     * Worldgen reads this from several threads at once. Entries are immutable and published by a plain array
     * write, so a reader either sees a complete entry or misses and rolls its own; a miss costs one
     * recomputation and nothing else.
     */
    private final CachedCell[] cells = new CachedCell[CELL_CACHE_SIZE];

    public CraterFieldFunction(final Holder<VoronoiSource> voronoiSource, final Mode mode) {
        this.voronoiSource = voronoiSource;
        this.mode = mode;
    }

    public Holder<VoronoiSource> voronoiSource() {
        return this.voronoiSource;
    }

    public Mode mode() {
        return this.mode;
    }

    @Override
    public double compute(final FunctionContext context) {
        VoronoiSource source = VoronoiSource.seeded(this.voronoiSource);
        Sample sample = this.sampleAt(source, context.blockX(), context.blockZ());
        return this.mode == Mode.DELTA ? sample.delta() : sample.damp();
    }

    /** The gather's whole answer: height delta in blocks, host-relief damp factor, and the crowding. */
    public record Sample(double delta, double damp, int covering) {}

    public Sample sampleAt(final VoronoiSource source, final double x, final double z) {
        long seed = craterSeed(source.seed());

        double[] deltas = new double[MAX_COVERING];
        double[] masks = new double[MAX_COVERING];
        double[] damps = new double[MAX_COVERING];
        double[] ages = new double[MAX_COVERING];
        int count = 0;

        for (int index = 0; index < LAYERS.size(); index++) {
            Layer layer = LAYERS.get(index);
            int baseX = Mth.floor(x / layer.cell());
            int baseZ = Mth.floor(z / layer.cell());

            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    Crater crater = this.crater(seed, source, index, baseX + dx, baseZ + dz);
                    if (crater == null || count >= MAX_COVERING) {
                        continue;
                    }

                    double radius = 0.5 * crater.diameter();
                    double offsetX = x - crater.centerX();
                    double offsetZ = z - crater.centerZ();
                    double r = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ) / radius;

                    double wobble = gradientNoise(seed,
                            offsetX / (WOBBLE_SPACING * radius) + crater.wobblePhase(),
                            offsetZ / (WOBBLE_SPACING * radius) + crater.wobblePhase());

                    // Culled on the true radius, wobbled after: the profile is already smoothed to zero at
                    // the footprint bound, so a column the wobble would have pulled in contributes nothing.
                    if (r > FOOTPRINT) {
                        continue;
                    }

                    double degradation = crater.degradation();
                    r = Math.max(r + WOBBLE_AMPLITUDE * (0.25 + degradation) * wobble, 0.0);

                    deltas[count] = craterDelta(r, crater.diameter(), degradation);
                    masks[count] = (1.0 - smoothstep(MASK_INNER, MASK_OUTER, r)) * (1.0 - MASK_AGE_LOSS * degradation);
                    damps[count] = DAMP_STRENGTH * Math.clamp(1.0 - degradation / DAMP_AGE_MAX, 0.0, 1.0)
                            * (1.0 - smoothstep(DAMP_INNER, DAMP_OUTER, r));
                    ages[count] = crater.ageKey();
                    count++;
                }
            }
        }

        double delta = 0.0;
        double damp = 1.0;

        for (int i = 0; i < count; i++) {
            double weight = 1.0;

            for (int j = 0; j < count; j++) {
                if (j != i && ages[j] < ages[i]) {
                    weight *= 1.0 - masks[j];
                }
            }

            delta += deltas[i] * weight;
            damp *= 1.0 - damps[i] * weight;
        }

        return new Sample(delta, damp, count);
    }

    /** One crater's height contribution in blocks, {@code r} in units of its rim radius. */
    public static double craterDelta(final double r, final double diameter, final double degradation) {
        double freshDepth = freshDepth(diameter);
        double depth = freshDepth * (1.0 - DEPTH_LOSS * degradation);
        double rim = RIM_RATIO * freshDepth * (1.0 - RIM_LOSS * degradation);

        if (r < 1.0) {
            double power = Mth.lerp(degradation, BOWL_POWER_FRESH, BOWL_POWER_OLD);
            return -depth + (depth + rim) * Math.pow(r, power);
        }

        double falloff = Mth.lerp(degradation, RIM_FALLOFF_FRESH, RIM_FALLOFF_OLD);
        return rim * Math.pow(r, -falloff) * (1.0 - smoothstep(FOOTPRINT - 0.5, FOOTPRINT, r));
    }

    public static double freshDepth(final double diameter) {
        return DEPTH_COEFFICIENT * Math.pow(diameter, DEPTH_EXPONENT);
    }

    /** The crater field's own seed, kept off the voronoi source's so the two fields cannot correlate. */
    public static long craterSeed(final long voronoiSeed) {
        return LatticeHash.mix(voronoiSeed + CRATER_SALT);
    }

    private Crater crater(final long seed, final VoronoiSource source, final int layer, final int cellX, final int cellZ) {
        int slot = (int) (LatticeHash.mix(((long) cellZ << 32 | cellX & 0xFFFFFFFFL) + layer * 0x9E3779B97F4A7C15L) & CELL_CACHE_MASK);
        CachedCell cached = this.cells[slot];

        if (cached != null && cached.layer() == layer && cached.cellX() == cellX && cached.cellZ() == cellZ) {
            return cached.crater();
        }

        Crater crater = rollCrater(seed, LAYERS.get(layer), cellX, cellZ, source);
        this.cells[slot] = new CachedCell(layer, cellX, cellZ, crater);
        return crater;
    }

    /**
     * Rolls one cell, or returns null if the cell holds no crater. The epoch is read at the rolled center,
     * which is the whole of the border-coherence guarantee.
     */
    public static Crater rollCrater(final long seed, final Layer layer, final int cellX, final int cellZ,
                                    final VoronoiSource epochSource) {
        long cellSeed = seed + layer.salt();

        double[] center = cellCenter(seed, layer, cellX, cellZ);
        double centerX = center[0];
        double centerZ = center[1];

        double oldness = oldnessAt(epochSource, centerX, centerZ);
        double presence = roll(cellSeed, cellX, cellZ, PRESENCE_SALT);

        if (presence >= Mth.lerp(Math.pow(oldness, DENSITY_GAMMA), layer.presenceYoung(), layer.presenceOld())) {
            return null;
        }

        double diameterRoll = roll(cellSeed, cellX, cellZ, DIAMETER_SALT);
        double range = 1.0 - Math.pow(layer.minDiameter() / layer.maxDiameter(), PARETO_EXPONENT);
        double diameter = layer.minDiameter() / Math.pow(1.0 - diameterRoll * range, 1.0 / PARETO_EXPONENT);

        double ageRoll = roll(cellSeed, cellX, cellZ, AGE_SALT);
        double degradation = Math.pow(ageRoll, Mth.lerp(oldness, AGE_SKEW_YOUNG, AGE_SKEW_OLD))
                * Mth.lerp(oldness, RETENTION_YOUNG, 1.0);

        return new Crater(centerX, centerZ, diameter, degradation,
                degradation + 0.001 * presence,
                roll(cellSeed, cellX, cellZ, WOBBLE_PHASE_SALT) * WOBBLE_PHASE_SCALE);
    }

    /** Where a cell's crater would sit, whether or not it rolled one. The epoch is read here, not at the column. */
    public static double[] cellCenter(final long seed, final Layer layer, final int cellX, final int cellZ) {
        long cellSeed = seed + layer.salt();
        return new double[]{
                (cellX + 0.5 + JITTER * (roll(cellSeed, cellX, cellZ, CENTER_X_SALT) - 0.5)) * layer.cell(),
                (cellZ + 0.5 + JITTER * (roll(cellSeed, cellX, cellZ, CENTER_Z_SALT) - 0.5)) * layer.cell()};
    }

    public static double oldnessAt(final VoronoiSource epochSource, final double worldX, final double worldZ) {
        return Math.clamp(0.5 * epochSource.epochAt(worldX, worldZ) + 0.5, 0.0, 1.0);
    }

    private static double roll(final long seed, final int cellX, final int cellZ, final long salt) {
        return LatticeHash.unitInterval(LatticeHash.hash(seed, cellX, cellZ, salt));
    }

    /** Gradient noise on the shared lattice hash, so the rim trace needs no registered noise of its own. */
    private static double gradientNoise(final long seed, final double x, final double z) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double fadeX = fade(fx);
        double fadeZ = fade(fz);

        double low = Mth.lerp(fadeX, dotGradient(seed, x0, z0, fx, fz), dotGradient(seed, x0 + 1, z0, fx - 1.0, fz));
        double high = Mth.lerp(fadeX, dotGradient(seed, x0, z0 + 1, fx, fz - 1.0), dotGradient(seed, x0 + 1, z0 + 1, fx - 1.0, fz - 1.0));
        return Mth.lerp(fadeZ, low, high) * Math.sqrt(2.0);
    }

    private static double dotGradient(final long seed, final int x, final int z, final double dx, final double dz) {
        double angle = roll(seed, x, z, WOBBLE_LATTICE_SALT) * (2.0 * Math.PI);
        return Math.cos(angle) * dx + Math.sin(angle) * dz;
    }

    private static double fade(final double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double smoothstep(final double from, final double to, final double value) {
        double t = Math.clamp((value - from) / (to - from), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** The deepest bowl any layer can roll, which bounds the delta channel once times {@link #MAX_COVERING}. */
    private static final double DEEPEST_BOWL = LAYERS.stream().mapToDouble(layer -> freshDepth(layer.maxDiameter())).max().orElseThrow();

    @Override
    public double minValue() {
        return this.mode == Mode.DELTA ? -MAX_COVERING * DEEPEST_BOWL : 0.0;
    }

    @Override
    public double maxValue() {
        return this.mode == Mode.DELTA ? MAX_COVERING * RIM_RATIO * DEEPEST_BOWL : 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
