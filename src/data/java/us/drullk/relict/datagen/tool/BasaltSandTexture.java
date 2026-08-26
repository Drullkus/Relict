package us.drullk.relict.datagen.tool;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

// Standalone impl of using Photoshop's to transfer Smooth Basalt colors onto Sand texture, beats doing it by hand
public final class BasaltSandTexture {

    private static final String SAND_RESOURCE = "assets/minecraft/textures/block/red_sand.png";
    private static final String BASALT_RESOURCE = "assets/minecraft/textures/block/smooth_basalt.png";
    private static final String OUTPUT_PATH = "assets/relict/textures/block/basalt_sand.png";

    private BasaltSandTexture() {
    }

    static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: BasaltSandTexture <path to src/main/resources>");
            System.exit(2);
            return;
        }

        BufferedImage sand = readVanillaTexture(SAND_RESOURCE);
        BufferedImage basalt = readVanillaTexture(BASALT_RESOURCE);

        int[] sandPalette = uniqueColorsByBrightness(sand);
        int[] basaltPalette = uniqueColorsByBrightness(basalt);

        BufferedImage output;
        String branch;
        if (sandPalette.length == basaltPalette.length) {
            branch = "EQUAL counts -> 1:1 brightness-ordered palette swap";
            output = recolorByPaletteSwap(sand, sandPalette, basaltPalette);
        } else {
            branch = "UNEQUAL counts -> min-max normalize + brightness gradient map";
            output = recolorByGradientMap(sand, basaltPalette);
        }

        Path outputFile = Path.of(args[0]).resolve(OUTPUT_PATH);
        Files.createDirectories(outputFile.getParent());
        ImageIO.write(output, "png", outputFile.toFile());

        int[] average = averageColor(output);

        System.out.println("Branch fired: " + branch);
        System.out.println("red_sand.png unique colors: " + sandPalette.length);
        System.out.println("smooth_basalt.png unique colors: " + basaltPalette.length);
        System.out.printf("Average output color: #%02x%02x%02x%n", average[0], average[1], average[2]);
        System.out.println("Wrote " + outputFile + " md5=" + md5Of(outputFile));
    }

    private static BufferedImage readVanillaTexture(String resource) {
        try (InputStream in = BasaltSandTexture.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource
                        + " (expected on the data sourceSet's runtime classpath, from the vanilla client jar)");
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Distinct ARGB pixel values, ascending by (brightness, packed value) — the tie-break keeps this deterministic. */
    private static int[] uniqueColorsByBrightness(BufferedImage image) {
        TreeSet<Integer> colors = new TreeSet<>(
                Comparator.comparingDouble(BasaltSandTexture::brightness).thenComparingInt(c -> c));
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getRGB(x, y));
            }
        }

        int[] result = new int[colors.size()];
        int i = 0;
        for (int color : colors) {
            result[i++] = color;
        }
        return result;
    }

    /** Standard luma weighting; alpha is ignored since these block textures are fully opaque. */
    private static double brightness(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }

    private static BufferedImage recolorByPaletteSwap(BufferedImage sand, int[] sandPalette, int[] basaltPalette) {
        Map<Integer, Integer> mapping = new LinkedHashMap<>();
        for (int i = 0; i < sandPalette.length; i++) {
            mapping.put(sandPalette[i], basaltPalette[i]);
        }

        BufferedImage output = new BufferedImage(sand.getWidth(), sand.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sand.getHeight(); y++) {
            for (int x = 0; x < sand.getWidth(); x++) {
                int source = sand.getRGB(x, y);
                int alpha = (source >>> 24) & 0xFF;
                int mapped = mapping.get(source);
                output.setRGB(x, y, (alpha << 24) | (mapped & 0xFFFFFF));
            }
        }
        return output;
    }

    private static BufferedImage recolorByGradientMap(BufferedImage sand, int[] basaltPalette) {
        int width = sand.getWidth();
        int height = sand.getHeight();

        double[][] luma = new double[height][width];
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double l = brightness(sand.getRGB(x, y));
                luma[y][x] = l;
                min = Math.min(min, l);
                max = Math.max(max, l);
            }
        }
        double range = max - min;

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int source = sand.getRGB(x, y);
                int alpha = (source >>> 24) & 0xFF;
                double gray = range <= 0.0 ? 0.5 : (luma[y][x] - min) / range;
                int mapped = gradientMap(basaltPalette, gray);
                output.setRGB(x, y, (alpha << 24) | (mapped & 0xFFFFFF));
            }
        }
        return output;
    }

    /** Interpolates within the brightness-sorted palette at normalized position {@code t} in [0, 1]. */
    private static int gradientMap(int[] palette, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double scaled = clamped * (palette.length - 1);
        int lowerIndex = (int) Math.floor(scaled);
        int upperIndex = Math.min(lowerIndex + 1, palette.length - 1);
        return lerpColor(palette[lowerIndex], palette[upperIndex], scaled - lowerIndex);
    }

    private static int lerpColor(int a, int b, double t) {
        int r = Math.round((float) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
        int g = Math.round((float) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
        int bl = Math.round((float) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t));
        return (r << 16) | (g << 8) | bl;
    }

    private static int[] averageColor(BufferedImage image) {
        long r = 0;
        long g = 0;
        long b = 0;
        int count = image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
            }
        }
        return new int[] {
                (int) Math.round(r / (double) count),
                (int) Math.round(g / (double) count),
                (int) Math.round(b / (double) count)
        };
    }

    private static String md5Of(Path path) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
