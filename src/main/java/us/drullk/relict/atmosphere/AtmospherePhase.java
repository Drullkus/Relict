package us.drullk.relict.atmosphere;

/** Seasonal position, derived from {@code pressure} by {@link AtmosphereCurve}; never stored on its own. */
public enum AtmospherePhase {
    VACUUM,
    FILLING,
    PRESENT,
    THINNING
}
