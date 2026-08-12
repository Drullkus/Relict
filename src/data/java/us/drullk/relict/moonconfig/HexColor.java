package us.drullk.relict.moonconfig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * {@code "#rrggbb"} in a config, packed 0xRRGGBB in memory.
 * <p>
 * Its own top-level class rather than a constant on {@link MoonSpriteConfig}: the nested config records
 * need it while the outer class is still initializing, and a field on the outer class would make that a
 * circular class-init dependency whose success depended on which codec a caller touched first.
 */
public final class HexColor {

    public static final Codec<Integer> CODEC = Codec.STRING.comapFlatMap(HexColor::parse, HexColor::toHex);

    private HexColor() {
    }

    private static DataResult<Integer> parse(String text) {
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.length() != 6) {
            return DataResult.error(() -> "Expected a #rrggbb colour, got: " + text);
        }

        try {
            return DataResult.success(Integer.parseInt(digits, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a hex colour: " + text);
        }
    }

    private static String toHex(int rgb) {
        return "#%06x".formatted(rgb & 0xFFFFFF);
    }

}
