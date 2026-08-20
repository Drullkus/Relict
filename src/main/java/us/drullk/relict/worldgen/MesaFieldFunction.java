package us.drullk.relict.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Fretted Mesas: two reference levels with the carve decided by a polygonal cell field.
 *
 * <h2>The grammar</h2>
 * A jittered-grid tessellation assigns every cell a level — caprock, valley floor, and in the strata variant
 * a mid stratum. The scarp is the boundary between differing levels, and its planform is
 * <strong>cornered by construction</strong>: cell walls are straight bisector segments meeting at angles, so
 * unions of same-level cells make angular outlines with concave corners without a single tuned parameter.
 * Two domain warps wave and serrate the wall lines; both are small against the cell pitch, so the corners
 * survive.
 *
 * <p>The cross-section is driven by {@code d}, the exact distance to the nearest differing-level wall: on the
 * upper side a flat cap and then a cliff down to the talus shoulder, on the lower side a concave debris
 * apron easing onto the floor. The cliff is monolithic on purpose — 78-85 degrees as prototyped — and must
 * not be softened for climbability.
 *
 * <h2>Zero at the floor</h2>
 * The field's zero is the valley floor at the province's own elevation; plateaus rise above it. A province
 * carrying this landform therefore reads as a floor with tablelands on it, not as a cap with holes cut down
 * through it.
 *
 * <p>The value returned is unitless: relief in units of {@link #REFERENCE_DEPTH}, so
 * {@link ProvinceParameter#MESA_AMPLITUDE} set to that number reproduces the prototype.
 */
public final class MesaFieldFunction implements DensityFunction {

    public static final MapCodec<MesaFieldFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("warp_x").forGetter(MesaFieldFunction::warpX),
            DensityFunction.NoiseHolder.CODEC.fieldOf("warp_z").forGetter(MesaFieldFunction::warpZ),
            DensityFunction.NoiseHolder.CODEC.fieldOf("serration_x").forGetter(MesaFieldFunction::serrationX),
            DensityFunction.NoiseHolder.CODEC.fieldOf("serration_z").forGetter(MesaFieldFunction::serrationZ),
            DensityFunction.NoiseHolder.CODEC.fieldOf("undulation").forGetter(MesaFieldFunction::undulation),
            DensityFunction.NoiseHolder.CODEC.fieldOf("selector").forGetter(MesaFieldFunction::selector)
    ).apply(instance, MesaFieldFunction::new));

    private static final KeyDispatchDataCodec<MesaFieldFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /**
     * Tessellation pitch, in blocks. Unlike every other knob below this one cannot follow the selector: a
     * lattice whose pitch varies with position does not tile, so the variants' 300-500 spread collapses to
     * one value and the degradation axis is carried by {@code capFraction} alone.
     */
    private static final double CELL = 440.0;

    /** Below 0.5 so a cell's site cannot leave the cell, which is what keeps the 3x3 nearest scan exact. */
    private static final double JITTER = 0.44;

    /**
     * Wall warp spacing, and <strong>the same for every variant</strong>. A noise sampled at {@code x / S(p)}
     * with a position-dependent {@code S} has gradient {@code 1/S - x S'/S^2}, whose second term grows without
     * bound with distance from the origin, so a spacing may never follow the selector. Only the amplitude may,
     * and it does.
     */
    private static final double WARP_SPACING = 300.0;

    private static final double SERRATION_AMPLITUDE = 6.0;
    private static final double SERRATION_SPACING = 70.0;

    private static final double UNDULATION_AMPLITUDE = 3.0;
    private static final double UNDULATION_SPACING = 1500.0;

    /** The blocks that {@link ProvinceParameter#MESA_AMPLITUDE} is measured in: the deepest variant's drop. */
    public static final double REFERENCE_DEPTH = 30.0;

    /**
     * Blocks over which two equidistant walls of <em>different</em> levels hand the cross-section over to each
     * other. Stratum-jog fix: with three levels the nearest differing neighbor can switch
     * between floor and mid along one cap cliff, and taking only the nearest wall makes the cliff height jump
     * there. Two levels have only one differing level to find, so this never fires on the two-level variants
     * and their cliffs stay exactly as prototyped.
     */
    private static final double JOG_BLEND = 6.0;

    /**
     * Cells per side of the patch that shares one level-probability draw. A cell's level must be a function of
     * the cell alone — read it from the observing column instead and the same cell would sit at two heights
     * depending on where it is seen from — so the selector is read on a coarse lattice of its own rather than
     * once per cell, which would cost a noise sample for each of the 25 cells the wall scan visits.
     */
    private static final int LEVEL_PATCH = 8;

    private static final long SITE_X_SALT = 0x27D4EB2F165667C5L;
    private static final long SITE_Z_SALT = 0x165667B19E3779F9L;
    private static final long LEVEL_SALT = 0x9E3779B97F4A7C15L;

    /** Off-lattice, so the sample this reads for a seed is not one of Perlin noise's exact zeros. */
    private static final double SEED_PROBE_X = 0.317;
    private static final double SEED_PROBE_Z = 0.719;

    private final DensityFunction.NoiseHolder warpX;
    private final DensityFunction.NoiseHolder warpZ;
    private final DensityFunction.NoiseHolder serrationX;
    private final DensityFunction.NoiseHolder serrationZ;
    private final DensityFunction.NoiseHolder undulation;
    private final DensityFunction.NoiseHolder selector;

    private volatile boolean seeded;
    private long seed;

    public MesaFieldFunction(final DensityFunction.NoiseHolder warpX, final DensityFunction.NoiseHolder warpZ,
                             final DensityFunction.NoiseHolder serrationX, final DensityFunction.NoiseHolder serrationZ,
                             final DensityFunction.NoiseHolder undulation, final DensityFunction.NoiseHolder selector) {
        this.warpX = warpX;
        this.warpZ = warpZ;
        this.serrationX = serrationX;
        this.serrationZ = serrationZ;
        this.undulation = undulation;
        this.selector = selector;
    }

    public DensityFunction.NoiseHolder warpX() {
        return this.warpX;
    }

    public DensityFunction.NoiseHolder warpZ() {
        return this.warpZ;
    }

    public DensityFunction.NoiseHolder serrationX() {
        return this.serrationX;
    }

    public DensityFunction.NoiseHolder serrationZ() {
        return this.serrationZ;
    }

    public DensityFunction.NoiseHolder undulation() {
        return this.undulation;
    }

    public DensityFunction.NoiseHolder selector() {
        return this.selector;
    }

    /** One variant's knob set. Everything here interpolates; the cell pitch, which cannot, is a constant. */
    private record Strata(double capFraction, double midFraction, double midLevel, double depth,
                          double talusFraction, double talusTangent, double cliffRun,
                          double benchFraction, double benchStart, double benchWidth,
                          double warpAmplitude) {

        static Strata lerp(final double t, final Strata a, final Strata b) {
            return new Strata(
                    Mth.lerp(t, a.capFraction, b.capFraction),
                    Mth.lerp(t, a.midFraction, b.midFraction),
                    Mth.lerp(t, a.midLevel, b.midLevel),
                    Mth.lerp(t, a.depth, b.depth),
                    Mth.lerp(t, a.talusFraction, b.talusFraction),
                    Mth.lerp(t, a.talusTangent, b.talusTangent),
                    Mth.lerp(t, a.cliffRun, b.cliffRun),
                    Mth.lerp(t, a.benchFraction, b.benchFraction),
                    Mth.lerp(t, a.benchStart, b.benchStart),
                    Mth.lerp(t, a.benchWidth, b.benchWidth),
                    Mth.lerp(t, a.warpAmplitude, b.warpAmplitude));
        }
    }

    /**
     * The four variants, in the order the selector walks them: fretted plateau, labyrinth, buttes,
     * strata. {@code capFraction} is the degradation axis — connected caprock with holes, then a labyrinth,
     * then isolated buttes — and the last variant adds the mid stratum and the benched double cliff.
     */
    private static final Strata[] VARIANTS = {
            new Strata(0.62, 0.00, 0.00, 26.0, 0.34, 0.45, 8.0, 0.00, 0.0, 0.0, 24.0),
            new Strata(0.45, 0.00, 0.00, 28.0, 0.34, 0.45, 7.0, 0.00, 0.0, 0.0, 24.0),
            new Strata(0.20, 0.00, 0.00, 22.0, 0.40, 0.42, 6.0, 0.00, 0.0, 0.0, 18.0),
            new Strata(0.44, 0.22, 0.55, 30.0, 0.30, 0.45, 6.0, 0.52, 7.0, 9.0, 24.0),
    };

    @Override
    public double compute(final FunctionContext context) {
        double x = context.blockX();
        double z = context.blockZ();
        Strata strata = this.strataAt(x, z);
        long latticeSeed = this.latticeSeed();

        double warpScale = 1.0 / WARP_SPACING;
        double warpedX = x + strata.warpAmplitude() / NoiseSpread.RATIO * this.warpX.getValue(x * warpScale, 0.0, z * warpScale)
                + SERRATION_AMPLITUDE / NoiseSpread.RATIO * this.serrationX.getValue(x / SERRATION_SPACING, 0.0, z / SERRATION_SPACING);
        double warpedZ = z + strata.warpAmplitude() / NoiseSpread.RATIO * this.warpZ.getValue(x * warpScale, 0.0, z * warpScale)
                + SERRATION_AMPLITUDE / NoiseSpread.RATIO * this.serrationZ.getValue(x / SERRATION_SPACING, 0.0, z / SERRATION_SPACING);

        double relief = this.mesaRelief(latticeSeed, strata, warpedX, warpedZ)
                + UNDULATION_AMPLITUDE / NoiseSpread.RATIO
                * this.undulation.getValue(x / UNDULATION_SPACING, 0.0, z / UNDULATION_SPACING);

        return relief / REFERENCE_DEPTH;
    }

    private Strata strataAt(final double x, final double z) {
        return blend(VariantSelector.at(this.selector, x, z, VARIANTS.length));
    }

    private static Strata blend(final double coordinate) {
        int lower = Math.min((int) coordinate, VARIANTS.length - 2);
        return Strata.lerp(coordinate - lower, VARIANTS[lower], VARIANTS[lower + 1]);
    }

    private double mesaRelief(final long latticeSeed, final Strata strata, final double x, final double z) {
        int gridX = Mth.floor(x / CELL);
        int gridZ = Mth.floor(z / CELL);

        int cellX = gridX;
        int cellZ = gridZ;
        double siteX = this.site(latticeSeed, gridX, gridZ, gridX, SITE_X_SALT);
        double siteZ = this.site(latticeSeed, gridX, gridZ, gridZ, SITE_Z_SALT);
        double nearest = square(siteX - x) + square(siteZ - z);

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int candidateX = gridX + dx;
                int candidateZ = gridZ + dz;
                double candidateSiteX = this.site(latticeSeed, candidateX, candidateZ, candidateX, SITE_X_SALT);
                double candidateSiteZ = this.site(latticeSeed, candidateX, candidateZ, candidateZ, SITE_Z_SALT);
                double distance = square(candidateSiteX - x) + square(candidateSiteZ - z);

                if (distance < nearest) {
                    nearest = distance;
                    cellX = candidateX;
                    cellZ = candidateZ;
                    siteX = candidateSiteX;
                    siteZ = candidateSiteZ;
                }
            }
        }

        double ownLevel = this.levelOf(latticeSeed, cellX, cellZ);

        double firstDistance = Double.MAX_VALUE;
        double firstLevel = ownLevel;
        double secondDistance = Double.MAX_VALUE;
        double secondLevel = ownLevel;

        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                int otherX = cellX + dx;
                int otherZ = cellZ + dz;
                double otherLevel = this.levelOf(latticeSeed, otherX, otherZ);
                if (otherLevel == ownLevel) {
                    continue;
                }

                double wall = bisectorDistance(x, z, siteX, siteZ,
                        this.site(latticeSeed, otherX, otherZ, otherX, SITE_X_SALT),
                        this.site(latticeSeed, otherX, otherZ, otherZ, SITE_Z_SALT));

                if (firstDistance == Double.MAX_VALUE || otherLevel == firstLevel) {
                    firstLevel = otherLevel;
                    firstDistance = Math.min(firstDistance, wall);
                } else if (secondDistance == Double.MAX_VALUE || otherLevel == secondLevel) {
                    secondLevel = otherLevel;
                    secondDistance = Math.min(secondDistance, wall);
                } else if (wall < secondDistance) {
                    secondLevel = otherLevel;
                    secondDistance = wall;
                }
            }
        }

        if (firstDistance == Double.MAX_VALUE) {
            return ownLevel * strata.depth();
        }

        double first = scarp(strata, firstDistance, ownLevel, firstLevel);
        if (secondDistance == Double.MAX_VALUE) {
            return first;
        }

        double second = scarp(strata, secondDistance, ownLevel, secondLevel);
        return Mth.lerp(smoothstep(-JOG_BLEND, JOG_BLEND, secondDistance - firstDistance), second, first);
    }

    /** Cap side: flat, then cliff, down to the talus shoulder. Floor side: concave debris apron. */
    private static double scarp(final Strata strata, final double wall, final double ownLevel, final double otherLevel) {
        double own = ownLevel * strata.depth();
        double other = otherLevel * strata.depth();
        double drop = Math.abs(own - other);
        double shoulder = strata.talusFraction() * drop;

        return Math.min(own, other) + (own > other ? cliff(strata, wall, shoulder, drop) : apron(strata, wall, shoulder));
    }

    private static double cliff(final Strata strata, final double wall, final double shoulder, final double drop) {
        double bench = shoulder + strata.benchFraction() * (drop - shoulder);
        double toBench = shoulder + (bench - shoulder) * smoothstep(0.0, strata.benchStart(), wall);
        double cliffStart = strata.benchStart() + strata.benchWidth();

        return toBench + (drop - bench) * smoothstep(cliffStart, cliffStart + strata.cliffRun(), wall);
    }

    private static double apron(final Strata strata, final double wall, final double shoulder) {
        double run = 2.0 * shoulder / strata.talusTangent();
        if (run <= 0.0) {
            return 0.0;
        }

        double t = Math.clamp(1.0 - wall / run, 0.0, 1.0);
        return shoulder * t * t;
    }

    /**
     * Signed distance to the perpendicular bisector of two sites, positive on the near site's side. The
     * technique is the one described at iquilezles.org/articles/voronoilines, reimplemented here.
     */
    private static double bisectorDistance(final double x, final double z, final double nearX, final double nearZ,
                                           final double farX, final double farZ) {
        double towardsX = farX - nearX;
        double towardsZ = farZ - nearZ;
        double length = Math.sqrt(towardsX * towardsX + towardsZ * towardsZ);
        if (length == 0.0) {
            return Double.MAX_VALUE;
        }

        return ((0.5 * (nearX + farX) - x) * towardsX + (0.5 * (nearZ + farZ) - z) * towardsZ) / length;
    }

    private double site(final long latticeSeed, final int cellX, final int cellZ, final int axis, final long salt) {
        double offset = LatticeHash.unitInterval(LatticeHash.hash(latticeSeed, cellX, cellZ, salt)) * 2.0 - 1.0;
        return (axis + 0.5 + JITTER * offset) * CELL;
    }

    /** 0 is the valley floor, 1 the caprock, and in the strata variant the mid stratum sits between them. */
    private double levelOf(final long latticeSeed, final int cellX, final int cellZ) {
        int patchX = Math.floorDiv(cellX, LEVEL_PATCH);
        int patchZ = Math.floorDiv(cellZ, LEVEL_PATCH);
        Strata strata = this.strataAtPatch(patchX, patchZ);

        double roll = LatticeHash.unitInterval(LatticeHash.hash(latticeSeed, cellX, cellZ, LEVEL_SALT));
        if (roll < strata.capFraction()) {
            return 1.0;
        }

        return roll < strata.capFraction() + strata.midFraction() ? strata.midLevel() : 0.0;
    }

    private Strata strataAtPatch(final int patchX, final int patchZ) {
        double patch = LEVEL_PATCH * CELL;
        return blend(VariantSelector.at(this.selector, (patchX + 0.5) * patch, (patchZ + 0.5) * patch, VARIANTS.length));
    }

    /**
     * Density functions are handed no world seed, so the only in-band source of one is the output of a noise
     * that {@code RandomState} already seeded from the world.
     */
    private long latticeSeed() {
        if (!this.seeded) {
            synchronized (this) {
                if (!this.seeded) {
                    this.seed = LatticeHash.mix(Double.doubleToRawLongBits(
                            this.undulation.getValue(SEED_PROBE_X, 0.0, SEED_PROBE_Z)));
                    this.seeded = true;
                }
            }
        }

        return this.seed;
    }

    private static double square(final double value) {
        return value * value;
    }

    private static double smoothstep(final double from, final double to, final double value) {
        if (to <= from) {
            return value < from ? 0.0 : 1.0;
        }

        double t = Math.clamp((value - from) / (to - from), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    @Override
    public void fillArray(final double[] array, final ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapChildren(final Visitor visitor) {
        return new MesaFieldFunction(visitor.visitNoise(this.warpX), visitor.visitNoise(this.warpZ),
                visitor.visitNoise(this.serrationX), visitor.visitNoise(this.serrationZ),
                visitor.visitNoise(this.undulation), visitor.visitNoise(this.selector));
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 2.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
