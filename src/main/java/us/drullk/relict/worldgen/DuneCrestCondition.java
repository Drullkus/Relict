package us.drullk.relict.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Surface-rule condition for {@code rusted_dunes}: true on {@link DuneCrest}'s crest band.
 *
 * <h2>Why a heightmap read, not a phase recompute</h2>
 * {@link SurfaceRules.Context} does not hand third-party conditions the column's own {@code blockX}/
 * {@code blockZ}, let alone the {@code RandomState} needed to resample {@link DuneWaveFunction}'s warp
 * and crenulation noise at its own scaled coordinates — those fields, and {@link SurfaceRules.Condition}
 * itself, are package-private (the latter is even {@code private}). {@code accesstransformer.cfg} widens
 * exactly the three members this class touches (the condition interface, and {@code Context.chunk}/
 * {@code blockX}/{@code blockZ}), the same access vanilla's own {@code steep()} has from inside
 * {@code SurfaceRules.java}.
 *
 * <p>Rather than reconstruct the phase, this reads {@code Heightmap.Types.WORLD_SURFACE_WG} — the same
 * already-filled heightmap {@code steep()} reads for its own neighbour comparison — so the crest test can
 * never drift from what the terrain shaping pass actually built, and variant blending needs no second
 * implementation here. The neighbour offsets are clamped into the current chunk's 0..15 local range,
 * exactly as {@code SteepMaterialCondition} clamps its own 1-block neighbours; near a chunk edge the reach
 * truncates the same way {@code steep()} accepts. [VANILLACOPY] the neighbour-heightmap technique of
 * {@code SurfaceRules.Context.SteepMaterialCondition}, generalised from a magnitude threshold to a
 * directional local-maximum test along the dune's own wind axis (see {@link DuneCrest}).
 */
public final class DuneCrestCondition implements SurfaceRules.ConditionSource {

    public static final DuneCrestCondition INSTANCE = new DuneCrestCondition();

    private static final MapCodec<DuneCrestCondition> MAP_CODEC = MapCodec.unit(INSTANCE);

    private DuneCrestCondition() {
    }

    @Override
    public MapCodec<DuneCrestCondition> codec() {
        return MAP_CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        return new SurfaceRules.Condition() {
            private long lastKey = Long.MIN_VALUE;
            private boolean lastResult;

            @Override
            public boolean test() {
                long key = ((long) context.blockX << 32) ^ (context.blockZ & 0xFFFFFFFFL);
                if (key != this.lastKey) {
                    this.lastKey = key;
                    int localX = context.blockX & 15;
                    int localZ = context.blockZ & 15;
                    this.lastResult = DuneCrest.isCrest((dx, dz) -> context.chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                            Mth.clamp(localX + dx, 0, 15), Mth.clamp(localZ + dz, 0, 15)));
                }

                return this.lastResult;
            }
        };
    }

}
