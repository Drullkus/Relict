package us.drullk.relict.datagen.celestial;

import com.google.common.hash.Hashing;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import us.drullk.relict.Relict;
import us.drullk.relict.init.worldgen.RelictDimension;
import us.drullk.relict.moonconfig.GlbModel;
import us.drullk.relict.moonconfig.MoonRasterizer;
import us.drullk.relict.moonconfig.MoonSpriteConfig;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Rasterizes the moon models into phase sprites on the celestials atlas.
 * <p>
 * Sprites land in {@code textures/environment/celestial/<moon>/phase_NN.png}, which the vanilla
 * {@code minecraft:celestials} atlas picks up on its own: its source is a directory listing, and those
 * span every namespace, so no atlas definition is needed.
 */
public class RelictCelestialSprites implements DataProvider {

    /** Frame counts come from {@link RelictDimension} so the renderer indexes exactly what was written. */
    private static final Map<String, Integer> MOONS = Map.of(
            "phobos", RelictDimension.PHOBOS_PHASES,
            "deimos", RelictDimension.DEIMOS_PHASES);
    private static final String CONFIG_DIR = "/moons/gen-configs/";
    private static final String SPRITE_DIR = "textures/environment/celestial";
    private static final int PREVIEW_SCALE = 8;

    private final PackOutput output;

    public RelictCelestialSprites(PackOutput output) {
        this.output = output;
    }

    @Override
    public String getName() {
        return "Celestial Sprites";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : new TreeMap<>(MOONS).entrySet()) {
            String moon = entry.getKey();
            int frames = entry.getValue();
            MoonSpriteConfig config = loadConfig(moon);
            GlbModel model = loadModel(config.model());
            MoonRasterizer.Result result = MoonRasterizer.render(model, config, frames);
            report(moon, model, config, result);

            Path assets = this.output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(Relict.MODID);
            for (int frame = 0; frame < result.frames().size(); frame++) {
                Path path = assets.resolve(SPRITE_DIR).resolve(config.spriteDir()).resolve("phase_%02d.png".formatted(frame));
                writes.add(writePng(cache, path, result.frames().get(frame)));
            }

            writePreviewSheet(moon, result.frames());
        }

        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private void report(String moon, GlbModel model, MoonSpriteConfig config, MoonRasterizer.Result result) {
        LOGGER.info("{}: {} frames from {} triangles at {}px reduced to {}px",
                moon, result.frames().size(), model.triangleCount(), config.renderSize(), config.body());

        if (result.levelsMeasured()) {
            LOGGER.info("{}: measured albedo levels min={} max={} — paste into the config to lock them in",
                    moon, result.levels().min(), result.levels().max());
        }

        // A little clipping at the top is wanted; only a starved or blown-out ramp is worth mentioning.
        if (result.peakValue() < 0.9F || result.peakValue() > 2.0F) {
            LOGGER.info("{}: peak lit value {} — lighting.gain {} would fill the gradient",
                    moon, result.peakValue(), config.lighting().gain() / Math.max(1.0E-3F, result.peakValue()));
        }

        if (!result.duplicates().isEmpty()) {
            LOGGER.warn("{}: frames {} are identical to their predecessor; {}px cannot express {} phases, consider lowering the count in RelictDimension",
                    moon, result.duplicates(), config.body(), result.frames().size());
        }
    }

    private static MoonSpriteConfig loadConfig(String moon) {
        String resource = CONFIG_DIR + moon + ".json";

        try (InputStream stream = RelictCelestialSprites.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing moon config: " + resource);
            }

            return MoonSpriteConfig.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
                    .getOrThrow(message -> new IllegalStateException(resource + ": " + message));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }

    private static GlbModel loadModel(String resource) {
        try (InputStream stream = RelictCelestialSprites.class.getResourceAsStream("/" + resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing moon model: " + resource);
            }

            return GlbModel.read(stream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }

    private static CompletableFuture<?> writePng(CachedOutput cache, Path path, BufferedImage image) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", bytes);
                cache.writeIfNeeded(path, bytes.toByteArray(), Hashing.sha1().hashBytes(bytes.toByteArray()));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not write " + path, e);
            }
        });
    }

    /**
     * Writes a filmstrip beside the generated pack for eyeballing orientation and phase spacing. It sits
     * outside the generated resource roots so it never ships, and on black because that is what additive
     * blending against a night sky actually looks like.
     */
    private void writePreviewSheet(String moon, List<BufferedImage> frames) {
        BufferedImage first = frames.getFirst();
        int width = first.getWidth() * PREVIEW_SCALE;
        int height = first.getHeight() * PREVIEW_SCALE;
        BufferedImage sheet = new BufferedImage(width * frames.size(), height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        for (int frame = 0; frame < frames.size(); frame++) {
            graphics.drawImage(frames.get(frame), frame * width, 0, width, height, null);
        }

        graphics.dispose();
        Path path = this.output.getOutputFolder().resolveSibling("moon-previews").resolve(moon + "_frames.png");

        try {
            Files.createDirectories(path.getParent());
            ImageIO.write(sheet, "PNG", path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path, e);
        }
    }

}
