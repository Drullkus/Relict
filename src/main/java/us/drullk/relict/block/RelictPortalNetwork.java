package us.drullk.relict.block;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import us.drullk.relict.Relict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class RelictPortalNetwork extends SavedData {

    private static final Codec<RelictPortalNetwork> CODEC = GlobalPos.CODEC.listOf().xmap(RelictPortalNetwork::new, network -> network.positions);

    public static final SavedDataType<RelictPortalNetwork> TYPE = new SavedDataType<>(Relict.id("portal_network"), RelictPortalNetwork::new, CODEC, DataFixTypes.LEVEL);

    private final List<GlobalPos> positions;

    public RelictPortalNetwork() {
        this(List.of());
    }

    private RelictPortalNetwork(List<GlobalPos> positions) {
        this.positions = new ArrayList<>(positions);
    }

    public static RelictPortalNetwork get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void remember(GlobalPos pos) {
        if (!this.positions.contains(pos)) {
            this.positions.add(pos);
            this.setDirty();
        }
    }

    /**
     * Returns the nearest remembered position in {@code dimension} within {@code radius} blocks that still
     * satisfies {@code stillValid} -- a stale entry (its portal has since been destroyed, see
     * {@link RelictPortalBlock#updateShape}) is pruned rather than returned.
     */
    public Optional<GlobalPos> findNearest(ResourceKey<Level> dimension, BlockPos near, int radius, Predicate<GlobalPos> stillValid) {
        double radiusSq = (double) radius * radius;
        List<GlobalPos> candidates = this.positions.stream()
                .filter(pos -> pos.dimension() == dimension)
                .filter(pos -> pos.pos().distSqr(near) <= radiusSq)
                .sorted(Comparator.comparingDouble(pos -> pos.pos().distSqr(near)))
                .toList();

        for (GlobalPos candidate : candidates) {
            if (stillValid.test(candidate)) {
                return Optional.of(candidate);
            }
            this.forget(candidate);
        }

        return Optional.empty();
    }

    public void forget(GlobalPos pos) {
        if (this.positions.remove(pos)) {
            this.setDirty();
        }
    }

}
