package us.drullk.relict.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import us.drullk.relict.Relict;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * The Keypad Rubbing's picture, held as a single shared {@link MapItemSavedData} (Route A) rather than
 * per-stack map data. Built from a 16,384-byte palette-index payload (one map-palette byte per pixel of a
 * 128x128 image, {@code MapColor.getColorFromPackedId} format) via {@link MapItemSavedData}'s private
 * {@code ByteBuffer}-colors constructor, ATed public for this -- see {@code accesstransformer.cfg}.
 * <p>
 * {@link us.drullk.relict.item.RubbingReloadListener} rebuilds this singleton from the live,
 * override-aware server resource manager on every datapack (re)load. See the impl report for the
 * client-availability litigation this leaves: in a two-JVM dedicated-server session, a datapack-only
 * override the producer drops on the server never reaches a client that hasn't had its own jar updated to
 * match, because nothing sends the palette bytes over the network -- no sync is invented here to close
 * that gap.
 */
public final class RubbingMapData {

    public static final int SIZE = 128;
    public static final int PIXEL_COUNT = SIZE * SIZE;
    public static final Identifier PAYLOAD_ID = Relict.id("cipher_chest/rubbing.bin");
    private static final String CLASSPATH_PAYLOAD_PATH = "data/relict/cipher_chest/rubbing.bin";

    private static volatile MapItemSavedData instance = buildFrom(readClasspathDefault());

    private RubbingMapData() {
    }

    public static MapItemSavedData get() {
        return instance;
    }

    /** Called by {@link RubbingReloadListener} after a successful datapack-driven reload. */
    static void reload(byte[] palette) {
        instance = buildFrom(palette);
    }

    private static MapItemSavedData buildFrom(byte[] palette) {
        ByteBuffer colors = ByteBuffer.wrap(palette.length == PIXEL_COUNT ? palette : new byte[PIXEL_COUNT]);
        return new MapItemSavedData(Level.OVERWORLD, 0, 0, (byte) 0, colors, false, false, true, List.of(), List.of());
    }

    private static byte[] readClasspathDefault() {
        try (InputStream stream = RubbingMapData.class.getClassLoader().getResourceAsStream(CLASSPATH_PAYLOAD_PATH)) {
            if (stream == null) {
                Relict.LOGGER.warn("Cipher chest rubbing payload not found on the classpath at {}; rubbing will render blank until a reload finds one", CLASSPATH_PAYLOAD_PATH);
                return new byte[PIXEL_COUNT];
            }
            byte[] bytes = stream.readAllBytes();
            return bytes.length == PIXEL_COUNT ? bytes : new byte[PIXEL_COUNT];
        } catch (IOException e) {
            Relict.LOGGER.warn("Failed to read the jar-shipped cipher chest rubbing payload", e);
            return new byte[PIXEL_COUNT];
        }
    }

}
