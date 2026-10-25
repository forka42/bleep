package io.github.pingsocc.bleep;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * What letter (or letters) a character can stand for: a set of a to z, as bits (bit 0 is a). Plain letters stand for
 * themselves, in any case, with any accent; letters from other alphabets that look like Latin ones stand for those;
 * leetspeak digits and symbols stand for the letters they're used for (some for more than one: {@code 1} is i or l).
 */
final class Letters {
    private Letters() {}

    /** Every letter: what a wildcard ({@code *}, as in {@code f*ck}) can stand for. */
    static final int ANY = (1 << 26) - 1;

    // Look-alikes from other alphabets (Cyrillic, Greek, and a few more), by what they look like.
    private static final Map<Integer, Integer> LOOK_ALIKES = new HashMap<>();
    // Digits and symbols used for letters, by the letters they're used for (the first, the likeliest).
    private static final Map<Integer, String> LEET = new HashMap<>();

    static {
        String[][] lookAlikes = {
                // Cyrillic
                {"а", "a"}, {"б", "b"}, {"в", "b"}, {"г", "r"}, {"е", "e"}, {"ё", "e"}, {"з", "z"}, {"и", "u"}, {"й", "u"},
                {"к", "k"}, {"м", "m"}, {"н", "h"}, {"о", "o"}, {"п", "n"}, {"р", "p"}, {"с", "c"}, {"т", "t"}, {"у", "y"},
                {"х", "x"}, {"ь", "b"}, {"ѕ", "s"}, {"і", "i"}, {"ї", "i"}, {"ј", "j"}, {"ԁ", "d"}, {"ԛ", "q"}, {"ԝ", "w"},
                {"ӏ", "l"}, {"һ", "h"}, {"ү", "y"},
                // Greek
                {"α", "a"}, {"β", "b"}, {"γ", "y"}, {"δ", "d"}, {"ε", "e"}, {"η", "n"}, {"ι", "i"}, {"κ", "k"}, {"ν", "v"},
                {"ο", "o"}, {"ρ", "p"}, {"τ", "t"}, {"υ", "u"}, {"χ", "x"}, {"ω", "w"}, {"ς", "c"},
                // Latin-like letters no accent-stripping turns into a plain one
                {"ı", "i"}, {"ł", "l"}, {"ø", "o"}, {"đ", "d"}, {"ħ", "h"}, {"ŧ", "t"}, {"ß", "b"}, {"æ", "a"}, {"œ", "o"},
                {"ɑ", "a"}, {"ɡ", "g"}, {"ʀ", "r"}, {"ɪ", "i"}, {"ʏ", "y"}, {"ᴄ", "c"}, {"ᴋ", "k"}, {"ᴜ", "u"}, {"ᴛ", "t"},
        };
        for (String[] l : lookAlikes) LOOK_ALIKES.put(l[0].codePointAt(0), l[1].charAt(0) - 'a');
        String[][] leet = {
                {"0", "o"}, {"1", "il"}, {"2", "z"}, {"3", "e"}, {"4", "a"}, {"5", "s"}, {"6", "gb"}, {"7", "t"}, {"8", "b"},
                {"9", "g"}, {"@", "a"}, {"$", "s"}, {"!", "i"}, {"|", "il"}, {"+", "t"}, {"€", "e"}, {"£", "l"}, {"¢", "c"},
                {"©", "c"}, {"®", "r"}, {"¥", "y"}, {"(", "c"}, {"<", "c"}, {"#", "h"}, {"&", "a"}, {"%", "x"},
        };
        for (String[] l : leet) LEET.put(l[0].codePointAt(0), l[1]);
    }

    /** The letters a letter stands for: itself (any case or accent) or what it looks like; 0 if none (a letter of another script). */
    static int ofLetter(int cp) {
        int lower = Character.toLowerCase(cp);
        if (lower >= 'a' && lower <= 'z') return 1 << (lower - 'a');
        Integer look = LOOK_ALIKES.get(lower);
        if (look != null) return 1 << look;
        // Accented (é, ü, ñ) or styled (full-width ｆ, mathematical 𝐟): its plain letter, once decomposed.
        String plain = Normalizer.normalize(new String(Character.toChars(lower)), Normalizer.Form.NFKD);
        for (int i = 0; i < plain.length(); ) {
            int c = plain.codePointAt(i);
            i += Character.charCount(c);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            int l = Character.toLowerCase(c);
            if (l >= 'a' && l <= 'z') return 1 << (l - 'a');
            Integer more = LOOK_ALIKES.get(l);
            if (more != null) return 1 << more;
            break;
        }
        return 0;
    }

    /** The letters a digit or symbol is used for in leetspeak; 0 if none. */
    static int ofLeet(int cp) {
        String s = LEET.get(cp);
        if (s == null) {
            // A full-width or otherwise styled digit or symbol: as the plain one.
            String plain = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFKC);
            if (plain.codePointCount(0, plain.length()) == 1 && plain.codePointAt(0) != cp) s = LEET.get(plain.codePointAt(0));
        }
        if (s == null) return 0;
        int mask = 0;
        for (int i = 0; i < s.length(); i++) mask |= 1 << (s.charAt(i) - 'a');
        return mask;
    }

    /** The likeliest letter of a set (its lowest, or for leet its first): for comparing words to the allow-list. */
    static char primary(int mask, int cp) {
        String s = LEET.get(cp);
        if (s != null) return s.charAt(0);
        return mask == 0 ? '?' : (char) ('a' + Integer.numberOfTrailingZeros(mask));
    }
}
