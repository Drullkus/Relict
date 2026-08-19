package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum ProvinceParameter implements StringRepresentable {
    EPOCH("epoch", -1.0, 1.0) {
        @Override
        public double compute(final VoronoiSource source, final int blockX, final int blockZ) {
            VoronoiSource.Cell cell = source.nearest(blockX, blockZ);
            return source.cellEpoch(cell.cellX(), cell.cellZ());
        }
    },
    DISTANCE_TO_CENTER("distance_to_center", 0.0, 1.0) {
        @Override
        public double compute(final VoronoiSource source, final int blockX, final int blockZ) {
            return Math.min(source.nearest(blockX, blockZ).distanceToCenter() / source.cellSize(), 1.0);
        }
    },
    BORDER_DISTANCE("border_distance", 0.0, 1.0) {
        @Override
        public double compute(final VoronoiSource source, final int blockX, final int blockZ) {
            VoronoiSource.Cell cell = source.nearest(blockX, blockZ);
            return Math.min((cell.distanceToSecondCenter() - cell.distanceToCenter()) / source.cellSize(), 1.0);
        }
    },
    PYRAMID("pyramid", 0.0, 1.0) {
        @Override
        public double compute(final VoronoiSource source, final int blockX, final int blockZ) {
            return Math.min(source.nearest(blockX, blockZ).edgeDistance() / source.cellSize(), 1.0);
        }
    },
    SURFACE_HEIGHT("surface_height", -1.0, 1.0) {
        @Override
        public double compute(final VoronoiSource source, final int blockX, final int blockZ) {
            // Signed, and zero means sea level. The router scales it by ELEVATION_SCALE and adds relief; no
            // pyramid term, because a province reads as terrain sitting at its elevation, not a cone.
            return source.blend(blockX, blockZ, source::cellElevation);
        }
    },
    RIDGE_AMPLITUDE("ridge_amplitude", 0.0, Province.MAX_RIDGE_AMPLITUDE) {
        @Override
        public double compute(final VoronoiSource source, final int blockX, final int blockZ) {
            return source.blend(blockX, blockZ, (province, cellX, cellZ) -> province.ridgeAmplitude());
        }
    },
    PLAIN_ROUGHNESS("plain_roughness", 0.0, Province.MAX_PLAIN_ROUGHNESS) {
        @Override
        public double compute(final VoronoiSource source, final int blockX, final int blockZ) {
            return source.blend(blockX, blockZ, (province, cellX, cellZ) -> province.plainRoughness());
        }
    };

    public static final Codec<ProvinceParameter> CODEC = StringRepresentable.fromEnum(ProvinceParameter::values);

    private final String name;
    private final double minValue;
    private final double maxValue;

    ProvinceParameter(final String name, final double minValue, final double maxValue) {
        this.name = name;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public abstract double compute(VoronoiSource source, int blockX, int blockZ);

    public double minValue() {
        return this.minValue;
    }

    public double maxValue() {
        return this.maxValue;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
