package us.drullk.relict.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.attribute.AttributeTypes;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;

/**
 * Sky positions for Mars's two moons, which vanilla has no attributes for.
 * <p>
 * Each moon needs three numbers, and all three are driven by that moon's orbit timeline rather than set on
 * the dimension type, so the whole orbit is described in one place and any of them can be given a curve
 * later without touching the renderer.
 * <ul>
 *     <li><em>angle</em> — where it sits along its arc, the direct analogue of vanilla's {@code sun_angle}.
 *     <li><em>inclination</em> — how far its plane is rolled off the sun's. Deimos holds one value, a
 *         single keyframe; Phobos's rocks between a shallow and a steep extreme over several sols. Walking
 *         it through zero, rather than rocking short of it, is what a transit season would be.
 *     <li><em>scale</em> — apparent size, which for a moon this close to Mars really does change across a
 *         crossing: the observer's distance to Phobos varies by half again between rising and zenith.
 * </ul>
 *
 * @see us.drullk.relict.init.worldgen.RelictDimension#PHOBOS_ROCK_SOLS
 */
public class RelictEnvironmentAttributes {

    public static final DeferredRegister<EnvironmentAttribute<?>> ENVIRONMENT_ATTRIBUTES =
            DeferredRegister.create(Registries.ENVIRONMENT_ATTRIBUTE, Relict.MODID);

    public static final EnvironmentAttribute<Float> PHOBOS_ANGLE = angle();
    public static final EnvironmentAttribute<Float> PHOBOS_INCLINATION = angle();
    public static final EnvironmentAttribute<Float> PHOBOS_SCALE = scale();
    public static final EnvironmentAttribute<Float> DEIMOS_ANGLE = angle();
    public static final EnvironmentAttribute<Float> DEIMOS_INCLINATION = angle();
    public static final EnvironmentAttribute<Float> DEIMOS_SCALE = scale();

    static {
        ENVIRONMENT_ATTRIBUTES.register("visual/phobos_angle", () -> PHOBOS_ANGLE);
        ENVIRONMENT_ATTRIBUTES.register("visual/phobos_inclination", () -> PHOBOS_INCLINATION);
        ENVIRONMENT_ATTRIBUTES.register("visual/phobos_scale", () -> PHOBOS_SCALE);
        ENVIRONMENT_ATTRIBUTES.register("visual/deimos_angle", () -> DEIMOS_ANGLE);
        ENVIRONMENT_ATTRIBUTES.register("visual/deimos_inclination", () -> DEIMOS_INCLINATION);
        ENVIRONMENT_ATTRIBUTES.register("visual/deimos_scale", () -> DEIMOS_SCALE);
    }

    /**
     * Syncable because the client draws from these and only the server runs the clock; spatially
     * interpolated to match vanilla's sky angles, so a biome could nudge one without the sky snapping at
     * the border.
     */
    private static EnvironmentAttribute<Float> angle() {
        return EnvironmentAttribute.builder(AttributeTypes.ANGLE_DEGREES)
                .defaultValue(0.0F)
                .spatiallyInterpolated()
                .syncable()
                .build();
    }

    /** A plain multiplier rather than an angle, so it lerps straight instead of taking a shortest path. */
    private static EnvironmentAttribute<Float> scale() {
        return EnvironmentAttribute.builder(AttributeTypes.FLOAT)
                .defaultValue(1.0F)
                .spatiallyInterpolated()
                .syncable()
                .build();
    }

    public static void register(IEventBus modEventBus) {
        ENVIRONMENT_ATTRIBUTES.register(modEventBus);
    }

}
