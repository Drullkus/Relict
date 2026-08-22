package us.drullk.relict.datagen.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.util.EasingType;
import net.minecraft.util.Keyframe;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.BooleanModifier;
import net.minecraft.world.attribute.modifier.ColorModifier;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.timeline.Timelines;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.List;


public class RelictTimelineGenerator {

    private static final int NO_TINT = 0xff_ff_ff_ff;

    // Overworld constants
    private static final int OVERWORLD_DAY_TICKS = 24000;
    private static final int SKY_DAWN = 133;
    private static final int SKY_DUSK = 11867;
    private static final int SKY_NIGHT_START = 13670;
    private static final int SKY_NIGHT_END = 22330;
    private static final int LIGHT_DAWN = 730;
    private static final int LIGHT_DUSK = 11270;
    private static final int LIGHT_NIGHT_START = 13140;
    private static final int LIGHT_NIGHT_END = 22860;
    private static final int MONSTER_NIGHT_START = 12542;
    private static final int MONSTER_NIGHT_END = 23460;
    private static final int NOON = 6000;

    private final int solTicks;
    private final OrbitTransitSolver orbitTransitSolver;

    public RelictTimelineGenerator() {
        this.solTicks = RelictDimension.SOL_TICKS;
        this.orbitTransitSolver = new OrbitTransitSolver(this.solTicks);
    }

    public void bootstrapTimelines(BootstrapContext<Timeline> context) {
        Holder<WorldClock> marsClock = context.lookup(Registries.WORLD_CLOCK).getOrThrow(RelictDimension.MARS_CLOCK);
        EasingType skyAngleEase = EasingType.symmetricCubicBezier(0.362F, 0.241F);
        final float nightSkyLight = Timelines.NIGHT_SKY_LIGHT_LEVEL / Timelines.DAY_SKY_LIGHT_LEVEL;

        KeyframeTrack<Float> sunAngle = new KeyframeTrack<>(List.of(
                new Keyframe<>(this.solTick(NOON), 360.0F),
                new Keyframe<>(this.solTick(NOON), 0.0F)), skyAngleEase);

        context.register(RelictDimension.MARS_SOL, Timeline.builder(marsClock)
                .setPeriodTicks(this.solTicks)

                .addTimeMarker(ClockTimeMarkers.DAY, this.solTick(1000), true)
                .addTimeMarker(ClockTimeMarkers.NOON, this.solTick(NOON), true)
                .addTimeMarker(ClockTimeMarkers.NIGHT, this.solTick(13000), true)
                .addTimeMarker(ClockTimeMarkers.MIDNIGHT, this.solTick(18000), true)
                .addTimeMarker(ClockTimeMarkers.WAKE_UP_FROM_SLEEP, 0)

                .addTrack(EnvironmentAttributes.SUN_ANGLE, track -> OrbitTransitSolver.replay(sunAngle, track))
                .addTrack(EnvironmentAttributes.MOON_ANGLE, track -> track.setEasing(skyAngleEase)
                        .addKeyframe(this.solTick(NOON), 540.0F)
                        .addKeyframe(this.solTick(NOON), 180.0F))
                .addTrack(EnvironmentAttributes.STAR_ANGLE, track -> track.setEasing(skyAngleEase)
                        .addKeyframe(this.solTick(NOON), 360.0F)
                        .addKeyframe(this.solTick(NOON), 0.0F))

                .addModifierTrack(EnvironmentAttributes.SKY_LIGHT_LEVEL, FloatModifier.MULTIPLY, track -> track
                        .addKeyframe(this.solTick(SKY_DAWN), 1.0F)
                        .addKeyframe(this.solTick(SKY_DUSK), 1.0F)
                        .addKeyframe(this.solTick(SKY_NIGHT_START), nightSkyLight)
                        .addKeyframe(this.solTick(SKY_NIGHT_END), nightSkyLight))
                .addModifierTrack(EnvironmentAttributes.SKY_LIGHT_COLOR, ColorModifier.MULTIPLY_RGB, track -> track
                        .addKeyframe(this.solTick(LIGHT_DAWN), NO_TINT)
                        .addKeyframe(this.solTick(LIGHT_DUSK), NO_TINT)
                        .addKeyframe(this.solTick(LIGHT_NIGHT_START), Timelines.NIGHT_SKY_LIGHT_COLOR)
                        .addKeyframe(this.solTick(LIGHT_NIGHT_END), Timelines.NIGHT_SKY_LIGHT_COLOR))
                .addModifierTrack(EnvironmentAttributes.SKY_LIGHT_FACTOR, FloatModifier.MULTIPLY, track -> track
                        .addKeyframe(this.solTick(LIGHT_DAWN), 1.0F)
                        .addKeyframe(this.solTick(LIGHT_DUSK), 1.0F)
                        .addKeyframe(this.solTick(LIGHT_NIGHT_START), Timelines.NIGHT_SKY_LIGHT_FACTOR)
                        .addKeyframe(this.solTick(LIGHT_NIGHT_END), Timelines.NIGHT_SKY_LIGHT_FACTOR))
                .addModifierTrack(EnvironmentAttributes.FOG_COLOR, ColorModifier.MULTIPLY_RGB, track -> track
                        .addKeyframe(this.solTick(SKY_DAWN), NO_TINT)
                        .addKeyframe(this.solTick(SKY_DUSK), NO_TINT)
                        .addKeyframe(this.solTick(SKY_NIGHT_START), Timelines.NIGHT_FOG_COLOR_MULTIPLIER_START)
                        .addKeyframe(this.solTick(SKY_NIGHT_END), Timelines.NIGHT_FOG_COLOR_MULTIPLIER_END))
                .addModifierTrack(EnvironmentAttributes.SKY_COLOR, ColorModifier.MULTIPLY_RGB, track -> track
                        .addKeyframe(this.solTick(SKY_DAWN), NO_TINT)
                        .addKeyframe(this.solTick(SKY_DUSK), NO_TINT)
                        .addKeyframe(this.solTick(SKY_NIGHT_START), Timelines.NIGHT_SKY_COLOR_MULTIPLIER)
                        .addKeyframe(this.solTick(SKY_NIGHT_END), Timelines.NIGHT_SKY_COLOR_MULTIPLIER))
                .addModifierTrack(EnvironmentAttributes.CLOUD_COLOR, ColorModifier.MULTIPLY_ARGB, track -> track
                        .addKeyframe(this.solTick(SKY_DAWN), NO_TINT)
                        .addKeyframe(this.solTick(SKY_DUSK), NO_TINT)
                        .addKeyframe(this.solTick(SKY_NIGHT_START), Timelines.NIGHT_CLOUD_COLOR_MULTIPLIER)
                        .addKeyframe(this.solTick(SKY_NIGHT_END), Timelines.NIGHT_CLOUD_COLOR_MULTIPLIER))
                .addTrack(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, track -> track
                        .addKeyframe(this.solTick(100), 0x47_8FAAD4)
                        .addKeyframe(this.solTick(1400), 0x00_B9CBE0)
                        .addKeyframe(this.solTick(10600), 0x00_B9CBE0)
                        .addKeyframe(this.solTick(11900), 0x47_8FAAD4)
                        .addKeyframe(this.solTick(12700), 0xCC_5E7CB8)
                        .addKeyframe(this.solTick(13000), 0xEB_4E6BA8)
                        .addKeyframe(this.solTick(13800), 0x99_38486A)
                        .addKeyframe(this.solTick(15400), 0x1A_1F273B)
                        .addKeyframe(this.solTick(16200), 0x00_171C2B)
                        .addKeyframe(this.solTick(19800), 0x00_171C2B)
                        .addKeyframe(this.solTick(20600), 0x1A_1F273B)
                        .addKeyframe(this.solTick(22200), 0x99_38486A)
                        .addKeyframe(this.solTick(23000), 0xEB_4E6BA8)
                        .addKeyframe(this.solTick(23300), 0xCC_5E7CB8))
                .addModifierTrack(EnvironmentAttributes.STAR_BRIGHTNESS, FloatModifier.MAXIMUM, track -> track
                        .addKeyframe(this.solTick(LIGHT_DAWN), 0.0F)
                        .addKeyframe(this.solTick(LIGHT_DUSK), 0.0F)
                        .addKeyframe(this.solTick(LIGHT_NIGHT_START), 0.5F)
                        .addKeyframe(this.solTick(LIGHT_NIGHT_END), 0.5F))
                .addModifierTrack(EnvironmentAttributes.MONSTERS_BURN, BooleanModifier.OR, track -> track
                        .addKeyframe(this.solTick(MONSTER_NIGHT_START), false)
                        .addKeyframe(this.solTick(MONSTER_NIGHT_END), true))
                .build());

        this.orbitTransitSolver.solveTransits(context, marsClock, sunAngle);
    }

    public void bootstrapWorldClocks(BootstrapContext<WorldClock> context) {
        context.register(RelictDimension.MARS_CLOCK, new WorldClock());
    }

    private int solTick(int overworldDayTick) {
        return Math.round(overworldDayTick * (float) this.solTicks / OVERWORLD_DAY_TICKS);
    }

}
