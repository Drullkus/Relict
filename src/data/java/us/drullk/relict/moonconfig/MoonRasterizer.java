package us.drullk.relict.moonconfig;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Renders a moon's phase frames on the CPU.
 * <p>
 * Deliberately not a GPU job: the whole set is a few million samples, and GPU rasterization is not
 * bit-reproducible across drivers, which would make committed sprites machine-dependent. Everything here
 * is plain float math so the same config always yields the same PNG.
 * <p>
 * Geometry is rasterized once — the moons are tidally locked, so only the light moves between frames.
 * <p>
 * Output pixels are opaque, with brightness carried entirely by colour: celestial bodies are drawn with
 * additive blending, so black is already invisible.
 * <p>
 * Edges are hard. Supersampling decides <em>where</em> the silhouette falls, never how soft it is — a pixel
 * is either the moon or it is not, because a feathered limb reads as a blurry sprite rather than pixel art.
 */
public final class MoonRasterizer {

    private static final float EDGE_MARGIN = 1.02F;
    private static final float LOW_PERCENTILE = 0.01F;
    private static final float HIGH_PERCENTILE = 0.99F;
    /** A body pixel needs this much of its area covered to be drawn at all. */
    private static final float COVERAGE_THRESHOLD = 0.5F;

    private MoonRasterizer() {
    }

    /**
     * @param frames         one sprite per phase, full first
     * @param levels         the luminance window used, whether configured or measured
     * @param levelsMeasured whether {@code levels} was measured rather than read from the config
     * @param peakValue      brightest lit value produced, gain included; 1 means the gradient's top stop is
     *                       reached, and anything well under it means the ramp's bright end is going unused
     * @param duplicates     indices whose pixels match the previous frame, meaning the frame count is
     *                       finer than the sprite size can express
     */
    public record Result(
            List<BufferedImage> frames,
            MoonSpriteConfig.Levels levels,
            boolean levelsMeasured,
            float peakValue,
            List<Integer> duplicates
    ) {
    }

    /**
     * @param frameCount phases to emit, owned by {@code RelictDimension} rather than the config so that
     *                   renderer, datagen, and tool cannot disagree
     */
    public static Result render(GlbModel model, MoonSpriteConfig config, int frameCount) {
        Surface surface = rasterize(model, config);

        boolean measured = config.levels().isEmpty();
        MoonSpriteConfig.Levels levels = config.levels().orElseGet(() -> measure(surface));

        float[] albedo = new float[surface.luminance.length];
        for (int sample = 0; sample < albedo.length; sample++) {
            albedo[sample] = surface.hit[sample] ? levels.normalize(surface.luminance[sample]) : 0.0F;
        }

        int[] halo = renderHalo(config);
        boolean[] drawn = drawnPixels(surface, config);
        boolean[] silhouette = silhouettePixels(drawn, config.body());
        List<BufferedImage> frames = new ArrayList<>(frameCount);
        float[] peak = new float[1];
        for (int frame = 0; frame < frameCount; frame++) {
            frames.add(compose(surface, albedo, halo, drawn, silhouette, config, frame, frameCount, peak));
        }

        return new Result(frames, levels, measured, peak[0], findDuplicates(frames));
    }

    /**
     * Which body pixels the moon occupies. Geometry does not change between frames, so coverage — and with
     * it the silhouette — is resolved once.
     */
    private static boolean[] drawnPixels(Surface surface, MoonSpriteConfig config) {
        int body = config.body();
        int supersample = config.supersample();
        float samplesPerPixel = supersample * (float) supersample;
        boolean[] drawn = new boolean[body * body];

        for (int pixelY = 0; pixelY < body; pixelY++) {
            for (int pixelX = 0; pixelX < body; pixelX++) {
                int hits = 0;

                for (int subY = 0; subY < supersample; subY++) {
                    int row = (pixelY * supersample + subY) * surface.size;
                    for (int subX = 0; subX < supersample; subX++) {
                        if (surface.hit[row + pixelX * supersample + subX]) {
                            hits++;
                        }
                    }
                }

                // All or nothing: the moon does not fade out over half a pixel.
                drawn[pixelY * body + pixelX] = hits / samplesPerPixel >= COVERAGE_THRESHOLD;
            }
        }

        return drawn;
    }

    /**
     * Body pixels with empty space among their eight neighbours, which is the outline the rim light draws.
     * <p>
     * Eight rather than four: with only orthogonal neighbours, a pixel tucked inside a diagonal step keeps
     * its dark value and punches a hole in the outline.
     */
    private static boolean[] silhouettePixels(boolean[] drawn, int body) {
        boolean[] silhouette = new boolean[drawn.length];

        for (int y = 0; y < body; y++) {
            for (int x = 0; x < body; x++) {
                if (!drawn[y * body + x]) {
                    continue;
                }

                for (int offsetY = -1; offsetY <= 1 && !silhouette[y * body + x]; offsetY++) {
                    for (int offsetX = -1; offsetX <= 1; offsetX++) {
                        int neighbourX = x + offsetX;
                        int neighbourY = y + offsetY;
                        // Outside the body box is space too, so a moon touching the edge still gets an outline.
                        boolean neighbourDrawn = neighbourX >= 0 && neighbourX < body && neighbourY >= 0 && neighbourY < body
                                && drawn[neighbourY * body + neighbourX];
                        if (!neighbourDrawn) {
                            silhouette[y * body + x] = true;
                            break;
                        }
                    }
                }
            }
        }

        return silhouette;
    }

    /**
     * Per-sample geometry of the visible hemisphere: view-space normals and raw albedo luminance. Light
     * plays no part here, so this is shared by every frame.
     * <p>
     * No vertical normal component: the sun stays in the moons' orbital plane, which is their equator, so
     * shading only ever needs the horizontal and viewer-facing components. Give the light an elevation and
     * this has to come back.
     */
    private record Surface(int size, boolean[] hit, float[] normalX, float[] normalZ, float[] luminance) {
    }

    private static Surface rasterize(GlbModel model, MoonSpriteConfig config) {
        MoonSpriteConfig.Orientation orientation = config.orientation();
        float[] view = orientation.view().vector();
        float[] up = orientation.up().vector();
        if (length(cross(up, view)) < 1.0E-4F) {
            throw new IllegalArgumentException("orientation view and up axes must differ");
        }

        float[] right = normalize(cross(up, view));
        rotate(up, orientation.yaw(), view);
        rotate(up, orientation.yaw(), right);
        rotate(right, orientation.pitch(), view);
        rotate(right, orientation.pitch(), up);
        rotate(view, orientation.roll(), up);
        rotate(view, orientation.roll(), right);

        // Re-orthonormalize: the three rotations above each move two of the vectors, so drift accumulates.
        view = normalize(view);
        float alongView = dot(up, view);
        up = normalize(new float[]{up[0] - view[0] * alongView, up[1] - view[1] * alongView, up[2] - view[2] * alongView});
        right = cross(up, view);

        int vertices = model.vertexCount();
        float[] screenX = new float[vertices];
        float[] screenY = new float[vertices];
        float[] depth = new float[vertices];
        float[] normalX = new float[vertices];
        float[] normalY = new float[vertices];
        float[] normalZ = new float[vertices];

        float[] centre = projectedCentre(model, right, up);
        float extent = 0.0F;
        for (int vertex = 0; vertex < vertices; vertex++) {
            float px = model.positionX(vertex);
            float py = model.positionY(vertex);
            float pz = model.positionZ(vertex);
            screenX[vertex] = px * right[0] + py * right[1] + pz * right[2] - centre[0];
            screenY[vertex] = px * up[0] + py * up[1] + pz * up[2] - centre[1];
            depth[vertex] = px * view[0] + py * view[1] + pz * view[2];
            float nx = model.normalX(vertex);
            float ny = model.normalY(vertex);
            float nz = model.normalZ(vertex);
            normalX[vertex] = nx * right[0] + ny * right[1] + nz * right[2];
            normalY[vertex] = nx * up[0] + ny * up[1] + nz * up[2];
            normalZ[vertex] = nx * view[0] + ny * view[1] + nz * view[2];
            extent = Math.max(extent, Math.max(Math.abs(screenX[vertex]), Math.abs(screenY[vertex])));
        }

        int size = config.renderSize();
        float half = size / 2.0F;
        float scale = half / (extent * EDGE_MARGIN);

        boolean[] hit = new boolean[size * size];
        float[] zBuffer = new float[size * size];
        Arrays.fill(zBuffer, Float.NEGATIVE_INFINITY);
        float[] outNormalX = new float[size * size];
        float[] outNormalZ = new float[size * size];
        float[] luminance = new float[size * size];
        Texture texture = new Texture(model.albedo());

        for (int triangle = 0; triangle < model.triangleCount(); triangle++) {
            int a = model.index(triangle * 3);
            int b = model.index(triangle * 3 + 1);
            int c = model.index(triangle * 3 + 2);

            float ax = half + screenX[a] * scale;
            float ay = half - screenY[a] * scale;
            float bx = half + screenX[b] * scale;
            float by = half - screenY[b] * scale;
            float cx = half + screenX[c] * scale;
            float cy = half - screenY[c] * scale;

            float area = edge(ax, ay, bx, by, cx, cy);
            if (Math.abs(area) < 1.0E-9F) {
                continue;
            }

            int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
            int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
            int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
            int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    float sampleX = x + 0.5F;
                    float sampleY = y + 0.5F;
                    float weightA = edge(bx, by, cx, cy, sampleX, sampleY) / area;
                    float weightB = edge(cx, cy, ax, ay, sampleX, sampleY) / area;
                    float weightC = 1.0F - weightA - weightB;
                    if (weightA < 0.0F || weightB < 0.0F || weightC < 0.0F) {
                        continue;
                    }

                    // Orthographic projection keeps screen-space interpolation exact, so no perspective divide.
                    float sampleDepth = weightA * depth[a] + weightB * depth[b] + weightC * depth[c];
                    int pixel = y * size + x;
                    if (sampleDepth <= zBuffer[pixel]) {
                        continue;
                    }

                    zBuffer[pixel] = sampleDepth;
                    hit[pixel] = true;
                    float nx = weightA * normalX[a] + weightB * normalX[b] + weightC * normalX[c];
                    float ny = weightA * normalY[a] + weightB * normalY[b] + weightC * normalY[c];
                    float nz = weightA * normalZ[a] + weightB * normalZ[b] + weightC * normalZ[c];
                    // ny takes part in the length even though it is not kept.
                    float inverse = 1.0F / Math.max(1.0E-6F, (float) Math.sqrt(nx * nx + ny * ny + nz * nz));
                    outNormalX[pixel] = nx * inverse;
                    outNormalZ[pixel] = nz * inverse;
                    luminance[pixel] = texture.luminance(
                            weightA * model.u(a) + weightB * model.u(b) + weightC * model.u(c),
                            weightA * model.v(a) + weightB * model.v(b) + weightC * model.v(c));
                }
            }
        }

        return new Surface(size, hit, outNormalX, outNormalZ, luminance);
    }

    /** Centres the sprite on the model's projected bounding box rather than its vertex average. */
    private static float[] projectedCentre(GlbModel model, float[] right, float[] up) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int vertex = 0; vertex < model.vertexCount(); vertex++) {
            float px = model.positionX(vertex);
            float py = model.positionY(vertex);
            float pz = model.positionZ(vertex);
            float x = px * right[0] + py * right[1] + pz * right[2];
            float y = px * up[0] + py * up[1] + pz * up[2];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        return new float[]{(minX + maxX) / 2.0F, (minY + maxY) / 2.0F};
    }

    /**
     * Measures the albedo window from the hemisphere actually facing the viewer, clipping outliers so a
     * few specks cannot flatten the whole ramp.
     */
    private static MoonSpriteConfig.Levels measure(Surface surface) {
        float[] visible = new float[surface.luminance.length];
        int count = 0;
        for (int sample = 0; sample < surface.luminance.length; sample++) {
            if (surface.hit[sample]) {
                visible[count++] = surface.luminance[sample];
            }
        }

        if (count == 0) {
            throw new IllegalStateException("Nothing was rasterized; check the orientation axes");
        }

        float[] sorted = Arrays.copyOf(visible, count);
        Arrays.sort(sorted);
        float low = sorted[Math.min(count - 1, (int) (count * LOW_PERCENTILE))];
        float high = sorted[Math.min(count - 1, (int) (count * HIGH_PERCENTILE))];
        return high - low < 1.0E-3F
                ? new MoonSpriteConfig.Levels(0.0F, Math.max(1.0E-3F, high))
                : new MoonSpriteConfig.Levels(low, high);
    }

    private static int[] renderHalo(MoonSpriteConfig config) {
        int canvas = config.canvas();
        MoonSpriteConfig.Halo halo = config.halo();
        float half = canvas / 2.0F;
        int[] pixels = new int[canvas * canvas];

        for (int y = 0; y < canvas; y++) {
            for (int x = 0; x < canvas; x++) {
                float offsetX = x + 0.5F - half;
                float offsetY = y + 0.5F - half;
                float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY) / half;
                if (distance > halo.outerRadius()) {
                    continue;
                }

                float position = (distance - halo.innerRadius()) / (halo.outerRadius() - halo.innerRadius());
                pixels[y * canvas + x] = halo.ramp().sample(position);
            }
        }

        return pixels;
    }

    private static BufferedImage compose(
            Surface surface,
            float[] albedo,
            int[] halo,
            boolean[] drawn,
            boolean[] silhouette,
            MoonSpriteConfig config,
            int frame,
            int frameCount,
            float[] peak
    ) {
        double angle = Math.toRadians(config.lighting().phaseOffset() + 360.0 * frame / frameCount);
        // The sun stays near the moons' orbital plane, which is the equator; +z faces the viewer, so an
        // angle of zero lights the disc head-on and 180 degrees is new.
        float lightX = (float) Math.sin(angle);
        float lightZ = (float) Math.cos(angle);
        float wrap = config.lighting().wrap();

        int body = config.body();
        int canvas = config.canvas();
        int supersample = config.supersample();
        int offset = (canvas - body) / 2;

        BufferedImage image = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < canvas; y++) {
            for (int x = 0; x < canvas; x++) {
                image.setRGB(x, y, 0xFF000000 | halo[y * canvas + x]);
            }
        }

        for (int pixelY = 0; pixelY < body; pixelY++) {
            for (int pixelX = 0; pixelX < body; pixelX++) {
                if (!drawn[pixelY * body + pixelX]) {
                    continue;
                }

                float total = 0.0F;
                int hits = 0;

                for (int subY = 0; subY < supersample; subY++) {
                    int row = (pixelY * supersample + subY) * surface.size;
                    for (int subX = 0; subX < supersample; subX++) {
                        int sample = row + pixelX * supersample + subX;
                        if (!surface.hit[sample]) {
                            continue;
                        }

                        float lambert = (surface.normalX[sample] * lightX + surface.normalZ[sample] * lightZ + wrap) / (1.0F + wrap);
                        total += albedo[sample] * Math.clamp(lambert, 0.0F, 1.0F);
                        hits++;
                    }
                }

                float rim = silhouette[pixelY * body + pixelX] ? config.rimLight().strength() : 0.0F;
                float lit = total / hits * config.lighting().gain() + rim;
                peak[0] = Math.max(peak[0], lit);
                float value = config.posterizeValue(Math.clamp(lit, 0.0F, 1.0F));
                // Overwrites the glow rather than adding to it: the body is opaque, and letting the glow
                // through would push the pixel back off the palette the snap just put it on.
                int rgb = config.snapToPalette(config.gradient().sample(value));
                image.setRGB(offset + pixelX, offset + pixelY, 0xFF000000 | rgb);
            }
        }

        return image;
    }

    private static List<Integer> findDuplicates(List<BufferedImage> frames) {
        List<Integer> duplicates = new ArrayList<>();
        for (int frame = 1; frame < frames.size(); frame++) {
            if (Arrays.equals(pixels(frames.get(frame)), pixels(frames.get(frame - 1)))) {
                duplicates.add(frame);
            }
        }

        return duplicates;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static float edge(float ax, float ay, float bx, float by, float x, float y) {
        return (bx - ax) * (y - ay) - (by - ay) * (x - ax);
    }

    /** Rotates {@code target} about {@code axis} by {@code degrees}, in place. */
    private static void rotate(float[] axis, float degrees, float[] target) {
        if (degrees == 0.0F) {
            return;
        }

        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float[] unit = normalize(axis);
        float[] crossed = cross(unit, target);
        float projected = dot(unit, target) * (1.0F - cos);

        for (int component = 0; component < 3; component++) {
            target[component] = target[component] * cos + crossed[component] * sin + unit[component] * projected;
        }
    }

    private static float[] cross(float[] first, float[] second) {
        return new float[]{
                first[1] * second[2] - first[2] * second[1],
                first[2] * second[0] - first[0] * second[2],
                first[0] * second[1] - first[1] * second[0]
        };
    }

    private static float dot(float[] first, float[] second) {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }

    private static float length(float[] vector) {
        return (float) Math.sqrt(dot(vector, vector));
    }

    private static float[] normalize(float[] vector) {
        float scale = 1.0F / Math.max(1.0E-6F, length(vector));
        return new float[]{vector[0] * scale, vector[1] * scale, vector[2] * scale};
    }

    /** Bilinear luminance lookup into the model's base colour texture. */
    private record Texture(BufferedImage image) {

        private float luminance(float u, float v) {
            float x = Math.clamp(u * (this.image.getWidth() - 1), 0.0F, this.image.getWidth() - 1.0F);
            float y = Math.clamp(v * (this.image.getHeight() - 1), 0.0F, this.image.getHeight() - 1.0F);
            int x0 = (int) x;
            int y0 = (int) y;
            int x1 = Math.min(this.image.getWidth() - 1, x0 + 1);
            int y1 = Math.min(this.image.getHeight() - 1, y0 + 1);
            float mixX = x - x0;
            float mixY = y - y0;

            float top = luminance(this.image.getRGB(x0, y0)) * (1.0F - mixX) + luminance(this.image.getRGB(x1, y0)) * mixX;
            float bottom = luminance(this.image.getRGB(x0, y1)) * (1.0F - mixX) + luminance(this.image.getRGB(x1, y1)) * mixX;
            return top * (1.0F - mixY) + bottom * mixY;
        }

        private static float luminance(int argb) {
            return (0.2126F * (argb >> 16 & 0xFF) + 0.7152F * (argb >> 8 & 0xFF) + 0.0722F * (argb & 0xFF)) / 255.0F;
        }

    }

}
