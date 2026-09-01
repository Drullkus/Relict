package us.drullk.relict.worldgen;

/**
 * The crest test for {@code rusted_dunes}: true on and just past the local topographic top of the dune
 * wave, tested along the wind axis against two already-known heights rather than by recomputing
 * {@link DuneWaveFunction}'s phase.
 *
 * <p>The profile a dune's height comes from ({@link DuneWaveFunction#compute}) is a long gentle stoss
 * climb into a short repose-angle slip fall, C1 across the join. That join is a genuine local maximum
 * along the wind direction: the downwind neighbour is lower (the slip face has started), and the upwind
 * neighbour is not much higher (the climb has topped out). Testing that shape needs only two height
 * samples a few blocks apart along the wind axis — it does not need the phase, the variant blend, or any
 * of the noise channels that produce it, so this is the one place in the dune grammar's Java that stays
 * correct even if the profile's numbers are retuned.
 *
 * <p>One copy of the thresholds, shared by the runtime surface rule ({@link DuneCrestCondition}, which
 * reads the world's own heightmap) and the report tooling's palette-map plates (which read the same
 * {@code terrain/dune_shape} density function the heightmap was built from) — so the rendered plate
 * cannot silently stop matching what the game renders.
 */
public final class DuneCrest {

    /** How far ahead/behind to look along the wind axis, in blocks. */
    public static final int SAMPLE_OFFSET = 4;

    /** The downwind neighbour must be at least this much lower — the slip face's brink, not the plain. */
    public static final double FALL_THRESHOLD = 0.5;

    /** The upwind neighbour may be at most this much higher — excludes the stoss climb, keeps the top. */
    public static final double RISE_SLACK = 1.5;

    static final int OFFSET_X = (int) Math.round(Math.cos(DuneWaveFunction.WIND_AZIMUTH) * SAMPLE_OFFSET);
    static final int OFFSET_Z = (int) Math.round(Math.sin(DuneWaveFunction.WIND_AZIMUTH) * SAMPLE_OFFSET);

    private DuneCrest() {
    }

    /** A height lookup relative to the column under test: {@code at(0, 0)} is that column itself. */
    @FunctionalInterface
    public interface RelativeHeight {
        double at(int offsetX, int offsetZ);
    }

    public static boolean isCrest(RelativeHeight height) {
        double here = height.at(0, 0);
        double fall = here - height.at(OFFSET_X, OFFSET_Z);
        double rise = here - height.at(-OFFSET_X, -OFFSET_Z);
        return fall > FALL_THRESHOLD && rise > -RISE_SLACK;
    }

}
