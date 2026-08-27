package us.drullk.relict.datagen.cipherchest;

import com.google.common.hash.Hashing;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import us.drullk.relict.Relict;
import us.drullk.relict.block.cipherchest.CipherChestSquare;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class CipherChestRubbingProvider implements DataProvider {

    private static final int SIZE = 128;
    private static final int CELL = SIZE / CipherChestSquare.SIZE; // 25px per cell (128/5, floored)
    private static final int PIXEL_COUNT = SIZE * SIZE;

    // Monochrome "stone rubbing" palette: background is the plain plate, strokes are the charcoal pass.
    // BACKGROUND is producer-hand-tuned and OFF-LIMITS to this lane -- carried over unchanged.
    private static final byte BACKGROUND = MapColor.STONE.getPackedId(Brightness.HIGH);
    private static final byte STROKE = MapColor.STONE.getPackedId(Brightness.LOWEST);

    // Wide-pixel diagonal zig-zag: chunky (BRUSH x BRUSH) cells stepped along a folded diagonal ramp. The
    // band marks GAPS, not strokes -- most of a digit stays inked (legible), with only thin diagonal
    // scratch lines cut through it for the hand-rubbed texture (frozen/producer-approved stroke treatment;
    // not touched by this lane).
    private static final int BRUSH = 2;
    private static final int ZIGZAG_PERIOD = 7;
    private static final int ZIGZAG_GAP_WIDTH = 2;

    private static final String DATA_SUBPATH = "cipher_chest/rubbing.bin";
    private static final String PREVIEW_FILE_NAME = "cipher-chest-rubbing-preview.png";

    private final PackOutput output;

    public CipherChestRubbingProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public String getName() {
        return "Cipher Chest Rubbing";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        byte[] palette = renderPalette();
        verifyIdempotent(palette);

        Path payloadPath = this.output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(Relict.MODID).resolve(DATA_SUBPATH);
        return CompletableFuture.runAsync(() -> {
            try {
                cache.writeIfNeeded(payloadPath, palette, Hashing.sha1().hashBytes(palette));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not write " + payloadPath, e);
            }
        });
    }

    // ---------------------------------------------------------------------------------------- rendering

    private static byte[] renderPalette() {
        boolean[][] ink = renderInkMask();

        byte[] palette = new byte[PIXEL_COUNT];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean stroked = ink[y][x] && !onZigzagGap(x, y);
                palette[y * SIZE + x] = stroked ? STROKE : BACKGROUND;
            }
        }
        return palette;
    }

    /** Rasterizes all 25 canon numbers (bold, centered per cell) to a binary "is this pixel glyph ink" mask. */
    private static boolean[][] renderInkMask() {
        BufferedImage glyphs = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = glyphs.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, SIZE, SIZE);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (CELL * 0.72)));

        for (int row = 0; row < CipherChestSquare.SIZE; row++) {
            for (int col = 0; col < CipherChestSquare.SIZE; col++) {
                String text = Integer.toString(CipherChestSquare.valueAt(row, col));
                int cellCenterX = col * CELL + CELL / 2;
                int cellCenterY = row * CELL + CELL / 2;
                int textWidth = g.getFontMetrics().stringWidth(text);
                int textAscent = g.getFontMetrics().getAscent();
                int textDescent = g.getFontMetrics().getDescent();
                int baselineX = cellCenterX - textWidth / 2;
                int baselineY = cellCenterY + (textAscent - textDescent) / 2;
                g.drawString(text, baselineX, baselineY);
            }
        }
        g.dispose();

        boolean[][] ink = new boolean[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int alpha = (glyphs.getRGB(x, y) >>> 24) & 0xFF;
                ink[y][x] = alpha > 127;
            }
        }
        return ink;
    }

    /** Wide-pixel (BRUSH x BRUSH) diagonal zig-zag gap line, folded so it travels back and forth, not a ramp. */
    private static boolean onZigzagGap(int x, int y) {
        int quantX = x / BRUSH;
        int quantY = y / BRUSH;
        int diagonal = Math.floorMod(quantX + quantY, ZIGZAG_PERIOD * 2);
        int folded = diagonal < ZIGZAG_PERIOD ? diagonal : (ZIGZAG_PERIOD * 2 - 1 - diagonal);
        return folded < ZIGZAG_GAP_WIDTH;
    }

    // ------------------------------------------------------------------------------------------- output

    private static void verifyIdempotent(byte[] expected) {
        byte[] rewritten = renderPalette();
        if (!java.util.Arrays.equals(rewritten, expected)) {
            throw new IllegalStateException("CipherChestRubbingProvider is not deterministic: two in-process renders of "
                    + "the same 128x128 palette produced different bytes");
        }
    }

}
