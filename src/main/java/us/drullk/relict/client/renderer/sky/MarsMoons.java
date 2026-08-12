package us.drullk.relict.client.renderer.sky;

import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttribute;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictEnvironmentAttributes;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The two moons, and everything the sky renderer needs to place one.
 * <p>
 * <strong>Declaration order is draw order</strong>, farthest first. Deimos orbits at 23,460 km and Phobos
 * at 9,376 km, so drawing them in this order means the nearer moon's occlusion mask lands on top of the
 * farther one and they occult each other correctly. Their declinations keep them 36 degrees apart today,
 * so it never comes up — but it costs nothing now and is already right if a precessing declination ever
 * brings them together.
 *
 * @param spriteDir  directory under {@code textures/environment/celestial}, matching the moon's gen-config
 * @param quadExtent half-width of the drawn quad at the celestial distance of 100
 */
public enum MarsMoons {

    DEIMOS("deimos", RelictDimension.DEIMOS_PHASES, RelictDimension.DEIMOS_QUAD_EXTENT,
            RelictEnvironmentAttributes.DEIMOS_ANGLE, RelictEnvironmentAttributes.DEIMOS_INCLINATION,
            RelictEnvironmentAttributes.DEIMOS_SCALE),
    PHOBOS("phobos", RelictDimension.PHOBOS_PHASES, RelictDimension.PHOBOS_QUAD_EXTENT,
            RelictEnvironmentAttributes.PHOBOS_ANGLE, RelictEnvironmentAttributes.PHOBOS_INCLINATION,
            RelictEnvironmentAttributes.PHOBOS_SCALE);

    private final List<Identifier> phaseSprites;
    private final Identifier occlusionSprite;
    private final float quadExtent;
    private final EnvironmentAttribute<Float> angle;
    private final EnvironmentAttribute<Float> inclination;
    private final EnvironmentAttribute<Float> scale;

    MarsMoons(String spriteDir, int phases, float quadExtent, EnvironmentAttribute<Float> angle,
              EnvironmentAttribute<Float> inclination, EnvironmentAttribute<Float> scale) {
        this.phaseSprites = IntStream.range(0, phases)
                .mapToObj(frame -> Relict.id("%s/phase_%02d".formatted(spriteDir, frame)))
                .toList();
        this.occlusionSprite = Relict.id(spriteDir + "/occlusion");
        this.quadExtent = quadExtent;
        this.angle = angle;
        this.inclination = inclination;
        this.scale = scale;
    }

    public List<Identifier> phaseSprites() {
        return this.phaseSprites;
    }

    public Identifier occlusionSprite() {
        return this.occlusionSprite;
    }

    public int phases() {
        return this.phaseSprites.size();
    }

    /**
     * Baseline half-width of the drawn quad, before the apparent-size curve scales it.
     * <p>
     * Note this covers the whole sprite, most of which is glow: a body is eight pixels within a
     * thirty-two-pixel canvas, so the visible disc is a quarter of this. The occlusion mask shares the
     * canvas, so it is drawn at the same extent and its own alpha confines the blot to the disc.
     */
    public float quadExtent() {
        return this.quadExtent;
    }

    public EnvironmentAttribute<Float> angle() {
        return this.angle;
    }

    public EnvironmentAttribute<Float> inclination() {
        return this.inclination;
    }

    public EnvironmentAttribute<Float> scale() {
        return this.scale;
    }

}
