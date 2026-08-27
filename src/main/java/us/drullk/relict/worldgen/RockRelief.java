package us.drullk.relict.worldgen;

/**
 * The one cheap "how bumpy is it near here" proxy the rock feature family uses everywhere it needs a
 * placement bias without sampling a raw density function. {@link Feature.FeaturePlaceContext} hands a
 * feature a {@code WorldGenLevel} and a config — no {@code RandomState}, so no direct read of {@code
 * relict:dune_wave} / {@code relict:mesa_field} / the ridge channel is available at placement time. A local
 * relief reading off four already-computed heightmap samples is the cheapest stand-in that still tells
 * ridge scarp from open plain, cliff talus from valley floor, and dune trough from dune body — the same
 * shape of trick {@link DuneCrest} already uses for the crest test, generalized from "is this a local
 * maximum along one axis" to "how much does height vary nearby, in every direction".
 */
public final class RockRelief {

    private RockRelief() {
    }

    /** The four cardinal offsets read around the center column, {@link DuneCrest.RelativeHeight}-style. */
    private static final int[][] CARDINAL_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Max minus min height sampled at {@code radius} blocks out from center in each cardinal direction. */
    public static double localRelief(DuneCrest.RelativeHeight height, int radius) {
        double center = height.at(0, 0);
        double max = center;
        double min = center;
        for (int[] offset : CARDINAL_OFFSETS) {
            double sample = height.at(offset[0] * radius, offset[1] * radius);
            max = Math.max(max, sample);
            min = Math.min(min, sample);
        }
        return max - min;
    }

}
