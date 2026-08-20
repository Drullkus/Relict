package us.drullk.relict.worldgen;

/** SplitMix64 over a 2D integer lattice: the shared hash behind every jittered-grid field in this mod. */
public final class LatticeHash {

    private static final long CELL_X_MULTIPLIER = 0x9E3779B97F4A7C15L;
    private static final long CELL_Z_MULTIPLIER = 0xC2B2AE3D27D4EB4FL;

    private LatticeHash() {
    }

    public static long hash(final long seed, final int cellX, final int cellZ, final long salt) {
        return mix(mix(mix(seed + salt) ^ cellX * CELL_X_MULTIPLIER) ^ cellZ * CELL_Z_MULTIPLIER);
    }

    public static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;
        return value;
    }

    public static double unitInterval(final long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

}
