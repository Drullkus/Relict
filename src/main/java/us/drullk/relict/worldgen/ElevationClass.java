package us.drullk.relict.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum ElevationClass implements StringRepresentable {
    LOW("low", -1.0),
    MID("mid", 0.0),
    HIGH("high", 1.0);

    public static final Codec<ElevationClass> CODEC = StringRepresentable.fromEnum(ElevationClass::values);

    private static final double FLOOR = 0.15;

    private static final double TOLERANCE = 1.5;

    private final String name;
    private final double preferredEpoch;

    ElevationClass(final String name, final double preferredEpoch) {
        this.name = name;
        this.preferredEpoch = preferredEpoch;
    }

    public double affinity(final double epoch) {
        return FLOOR + (1.0 - FLOOR) * Math.max(0.0, 1.0 - Math.abs(epoch - this.preferredEpoch) / TOLERANCE);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
