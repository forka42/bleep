package io.github.forka42.bleep;

/**
 * A message as Bleep reads it: a row of units, each a character that stands for a letter (or letters, or none), with
 * where it is in the original text; and, between them, where words break (spaces, punctuation). Invisible characters
 * (zero-width spaces and joiners, used to slip a word past a filter) and accents written apart from their letter are
 * part of the unit before them, not breaks.
 */
final class Text {
    final String original;
    int n;
    int[] mask = new int[16];              // the letters each unit stands for (Letters)
    int[] from = new int[16], to = new int[16];   // where it is in the original (char indices, to exclusive)
    int[] cp = new int[16];                // its character
    boolean[] symbol = new boolean[16];    // a digit or symbol standing in for a letter (not a letter itself)
    boolean[] wild = new boolean[16];      // a wildcard (*): any letter
    boolean[] gapBefore = new boolean[16]; // a break (space, punctuation) between it and the unit before

    Text(String s) {
        original = s;
        boolean gap = true;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i), len = Character.charCount(c);
            int type = Character.getType(c);
            if (type == Character.FORMAT || c == '­' || c == '͏') {      // (invisible: zero-width, soft hyphen)
                if (n > 0 && !gap) to[n - 1] = i + len;
                i += len;
                continue;
            }
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK) {
                if (n > 0 && !gap) to[n - 1] = i + len;                           // (an accent, its letter's)
                i += len;
                continue;
            }
            int m;
            boolean isSymbol = false, isWild = false;
            if (Character.isLetter(c)) m = Letters.ofLetter(c);
            else if (c == '*') { m = Letters.ANY; isWild = true; isSymbol = true; }
            else {
                m = Letters.ofLeet(c);
                if (m == 0 && !Character.isDigit(c)) { gap = true; i += len; continue; }   // (a break: space, punctuation)
                isSymbol = true;
            }
            add(m, c, i, i + len, isSymbol, isWild, gap);
            gap = false;
            i += len;
        }
    }

    private void add(int m, int c, int start, int end, boolean isSymbol, boolean isWild, boolean gap) {
        if (n == mask.length) {
            int size = n * 2;
            mask = java.util.Arrays.copyOf(mask, size); from = java.util.Arrays.copyOf(from, size); to = java.util.Arrays.copyOf(to, size);
            cp = java.util.Arrays.copyOf(cp, size); symbol = java.util.Arrays.copyOf(symbol, size); wild = java.util.Arrays.copyOf(wild, size);
            gapBefore = java.util.Arrays.copyOf(gapBefore, size);
        }
        mask[n] = m; cp[n] = c; from[n] = start; to[n] = end; symbol[n] = isSymbol; wild[n] = isWild; gapBefore[n] = gap;
        n++;
    }

    /** The units from a to b (exclusive) as plain letters (each its likeliest), for the allow-list. */
    String plain(int a, int b) {
        StringBuilder out = new StringBuilder(b - a);
        for (int i = a; i < b; i++) out.append(Letters.primary(mask[i], cp[i]));
        return out.toString();
    }
}
