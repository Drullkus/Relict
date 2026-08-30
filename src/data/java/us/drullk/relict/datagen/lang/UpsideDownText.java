package us.drullk.relict.datagen.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 180-degree text flip for {@code en_ud}. Mekanism's <a href="https://github.com/mekanism/Mekanism/blob/4e697bb623346aaf51f2fe80aca1c28e7b619f78/src/datagen/main/java/mekanism/client/lang/UpsideDownLanguageProvider.java">{@code UpsideDownLanguageProvider}</a> used as reference.
 */
final class UpsideDownText {

    // "%%" must be tried before the specifier alternative, or a literal-percent escape gets
    // parsed as a spec with a stray space flag that swallows the following letter.
    private static final Pattern FORMAT_SPECIFIER = Pattern.compile("%%|%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z])");
    private static final Pattern EXPLICIT_INDEX = Pattern.compile("^%(\\d+)\\$(.*)$", Pattern.DOTALL);

    private static final Map<Character, Character> GLYPHS = Map.ofEntries(
            Map.entry('a', 'ɐ'), Map.entry('b', 'q'), Map.entry('c', 'ɔ'), Map.entry('d', 'p'),
            Map.entry('e', 'ǝ'), Map.entry('f', 'ɟ'), Map.entry('g', 'ᵷ'), Map.entry('h', 'ɥ'),
            Map.entry('i', 'ᴉ'), Map.entry('j', 'ɾ'), Map.entry('k', 'ʞ'), Map.entry('l', 'ꞁ'),
            Map.entry('m', 'ɯ'), Map.entry('n', 'u'), Map.entry('o', 'o'), Map.entry('p', 'd'),
            Map.entry('q', 'b'), Map.entry('r', 'ɹ'), Map.entry('s', 's'), Map.entry('t', 'ʇ'),
            Map.entry('u', 'n'), Map.entry('v', 'ʌ'), Map.entry('w', 'ʍ'), Map.entry('x', 'x'),
            Map.entry('y', 'ʎ'), Map.entry('z', 'z'), Map.entry('A', 'Ɐ'), Map.entry('B', 'ᗺ'),
            Map.entry('C', 'Ɔ'), Map.entry('D', 'ᗡ'), Map.entry('E', 'Ǝ'), Map.entry('F', 'Ⅎ'),
            Map.entry('G', '⅁'), Map.entry('H', 'H'), Map.entry('I', 'I'), Map.entry('J', 'Ր'),
            Map.entry('K', 'Ʞ'), Map.entry('L', 'Ꞁ'), Map.entry('M', 'W'), Map.entry('N', 'N'),
            Map.entry('O', 'O'), Map.entry('P', 'Ԁ'), Map.entry('Q', 'Ꝺ'), Map.entry('R', 'ᴚ'),
            Map.entry('S', 'S'), Map.entry('T', '⟘'), Map.entry('U', '∩'), Map.entry('V', 'Λ'),
            Map.entry('W', 'M'), Map.entry('X', 'X'), Map.entry('Y', '⅄'), Map.entry('Z', 'Z'),
            Map.entry('0', '0'), Map.entry('1', '⥝'), Map.entry('2', 'ᘔ'), Map.entry('3', 'Ɛ'),
            Map.entry('4', '߈'), Map.entry('5', 'ϛ'), Map.entry('6', '9'), Map.entry('7', 'ㄥ'),
            Map.entry('8', '8'), Map.entry('9', '6'), Map.entry(',', '\''), Map.entry('.', '˙'),
            Map.entry('?', '¿'), Map.entry('!', '¡'), Map.entry(';', '؛'), Map.entry('"', '„'),
            Map.entry('\'', ','), Map.entry('`', ','), Map.entry('&', '⅋'), Map.entry('_', '‾'),
            Map.entry('^', 'v'), Map.entry('(', ')'), Map.entry(')', '('), Map.entry('[', ']'),
            Map.entry(']', '['), Map.entry('{', '}'), Map.entry('}', '{'), Map.entry('<', '>'),
            Map.entry('>', '<'), Map.entry('≤', '⪖'), Map.entry('≥', '⪕')
    );

    private UpsideDownText() {
    }

    static String flip(String value) {
        List<String> segments = new ArrayList<>();
        List<Boolean> isSpecifier = new ArrayList<>();
        Matcher matcher = FORMAT_SPECIFIER.matcher(value);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                segments.add(value.substring(cursor, matcher.start()));
                isSpecifier.add(false);
            }
            segments.add(matcher.group());
            isSpecifier.add(!"%%".equals(matcher.group()));
            cursor = matcher.end();
        }
        if (cursor < value.length()) {
            segments.add(value.substring(cursor));
            isSpecifier.add(false);
        }

        int numArguments = (int) isSpecifier.stream().filter(Boolean::booleanValue).count();
        StringBuilder result = new StringBuilder(value.length());
        int position = numArguments;
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (isSpecifier.get(i)) {
                result.append(reindexSpecifier(segments.get(i), position, numArguments));
                position--;
            } else {
                appendFlipped(result, segments.get(i));
            }
        }
        return result.toString();
    }

    // An unindexed specifier binds by position; flipping moves it, so it needs an explicit index.
    private static String reindexSpecifier(String code, int curIndex, int numArguments) {
        Matcher explicit = EXPLICIT_INDEX.matcher(code);
        int storedIndex;
        String ending;
        if (explicit.matches()) {
            storedIndex = Integer.parseInt(explicit.group(1));
            ending = explicit.group(2);
        } else {
            storedIndex = curIndex;
            ending = code.substring(1);
        }
        int newImplicitPosition = numArguments - curIndex + 1;
        return storedIndex == newImplicitPosition ? "%" + ending : "%" + storedIndex + "$" + ending;
    }

    private static void appendFlipped(StringBuilder result, String literal) {
        for (int i = literal.length() - 1; i >= 0; i--) {
            char c = literal.charAt(i);
            result.append(GLYPHS.getOrDefault(c, c));
        }
    }

}
