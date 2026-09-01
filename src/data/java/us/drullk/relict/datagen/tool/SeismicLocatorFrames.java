package us.drullk.relict.datagen.tool;

import com.google.common.hash.Hashing;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import us.drullk.relict.Relict;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Slices the seismic locator's vertical frame strip into the individual textures the vanilla-shaped
 * compass item model definition (see {@code RelictModels}) expects.
 */
public final class SeismicLocatorFrames implements DataProvider {

    private static final String SOURCE_RESOURCE = "/seismic_locator/seismic_locator.png";
    private static final String OUTPUT_DIR = "textures/item";
    private static final int FRAME_SIZE = 16;
    private static final int FRAME_COUNT = 32;

    private final PackOutput output;

    public SeismicLocatorFrames(PackOutput output) {
        this.output = output;
    }

    @Override
    public String getName() {
        return "Seismic Locator Frames";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        BufferedImage strip = readStrip();
        Path assets = this.output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(Relict.MODID).resolve(OUTPUT_DIR);

        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (int i = 0; i < FRAME_COUNT; i++) {
            BufferedImage frame = extractFrame(strip, i);
            writes.add(writePng(cache, assets.resolve("seismic_locator_%02d.png".formatted(i)), frame));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private static BufferedImage readStrip() {
        try (InputStream in = SeismicLocatorFrames.class.getResourceAsStream(SOURCE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + SOURCE_RESOURCE
                        + " (expected on the data sourceSet's resources, at src/data/resources)");
            }
            BufferedImage strip = ImageIO.read(in);
            if (strip.getWidth() != FRAME_SIZE || strip.getHeight() != FRAME_SIZE * FRAME_COUNT) {
                throw new IllegalStateException("seismic_locator.png is %dx%d, expected %dx%d (%d frames of %dx%d)"
                        .formatted(strip.getWidth(), strip.getHeight(), FRAME_SIZE, FRAME_SIZE * FRAME_COUNT, FRAME_COUNT, FRAME_SIZE, FRAME_SIZE));
            }
            return strip;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copies frame {@code index} (rows {@code index*FRAME_SIZE}..) into a fresh, unshared raster. */
    private static BufferedImage extractFrame(BufferedImage strip, int index) {
        BufferedImage frame = new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
        int yOffset = FRAME_SIZE * index;
        for (int y = 0; y < FRAME_SIZE; y++) {
            for (int x = 0; x < FRAME_SIZE; x++) {
                frame.setRGB(x, y, strip.getRGB(x, yOffset + y));
            }
        }
        return frame;
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

}
