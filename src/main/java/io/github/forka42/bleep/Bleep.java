package io.github.forka42.bleep;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A swear filter: finds swears, slurs and sexual terms in text, disguised or not, and masks them.
 * <p>
 * It sees through leetspeak and symbols ({@code sh1t}, {@code $hit}), letters from other alphabets that look like Latin
 * ones, accents, spaces and punctuation between the letters ({@code f.u.c.k}, {@code f u c k}), stretched letters
 * ({@code fuuuck}) and wildcards ({@code f*ck}), and leaves innocent words that contain a swear alone ({@code class},
 * {@code Scunthorpe}). Only what it matched is masked; the rest of the text is kept as it was written.
 * <p>
 * Immutable and safe to share between threads: build one and use it everywhere.
 *
 * <pre>{@code
 * Bleep bleep = Bleep.standard();
 * bleep.clean("what the f.u.c.k");    // "what the #######"
 * bleep.contains("sh1t happens");     // true
 * }</pre>
 */
public final class Bleep {
    /** How strong a word is: strong ones (swears, slurs, sexual terms) are masked by default, mild ones on request. */
    public enum Tier { STRONG, MILD }

    /**
     * A word found: where it is in the text (char indices, end exclusive), the listed word it is, and its tier.
     *
     * @param start where it begins in the text
     * @param end   where it ends in the text (exclusive)
     * @param word  the listed word it matched (plain lowercase letters)
     * @param tier  the word's tier
     */
    public record Match(int start, int end, String word, Tier tier) {}

    private final WordList words;
    private final Allow allow;
    private final char replacement;
    private final boolean wholeWords;

    private Bleep(WordList words, Allow allow, char replacement, boolean wholeWords) {
        this.words = words; this.allow = allow; this.replacement = replacement; this.wholeWords = wholeWords;
    }

    private static volatile Bleep standard;

    /** The built-in English list, strong words only, masking with {@code #}. */
    public static Bleep standard() {
        Bleep b = standard;
        if (b == null) standard = b = builder().standard().build();
        return b;
    }

    /** A filter of your own: start from the built-in list ({@link Builder#standard()}) or from nothing. */
    public static Builder builder() { return new Builder(); }

    /** Whether the text has anything in it to mask. */
    public boolean contains(String text) { return !find(text).isEmpty(); }

    /** The text with what's found masked, a mask character for each character of it (spaces kept). */
    public String clean(String text) {
        List<Match> found = find(text);
        if (found.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length());
        int at = 0;
        for (Match m : found) {
            out.append(text, at, m.start());
            for (int i = m.start(); i < m.end(); ) {
                int c = text.codePointAt(i);
                i += Character.charCount(c);
                if (Character.isWhitespace(c)) out.appendCodePoint(c); else out.append(replacement);
            }
            at = m.end();
        }
        out.append(text, at, text.length());
        return out.toString();
    }

    /** What's to mask in the text, in order, none overlapping. */
    public List<Match> find(String text) {
        if (text == null || text.isEmpty()) return List.of();
        Text t = new Text(text);
        List<int[]> spans = new ArrayList<>();                         // {first unit, end unit, entry index}
        List<WordList.Entry> entries = new ArrayList<>();
        // Each word on its own.
        for (int a = 0; a < t.n; ) {
            int b = a + 1;
            while (b < t.n && !t.gapBefore[b]) b++;
            scan(t, a, b, wholeWords, spans, entries);
            a = b;
        }
        // Letters spaced out (f u c k, f.u.c.k): a run of three or more single letters, as one word.
        for (int a = 0; a < t.n; ) {
            int b = a;
            while (b < t.n && single(t, b)) b++;
            if (b - a >= 3) scan(t, a, b, false, spans, entries);
            a = Math.max(a + 1, b);
        }
        if (spans.isEmpty()) return List.of();
        // The earliest first, the longest of those; none overlapping.
        spans.sort((x, y) -> x[0] != y[0] ? Integer.compare(x[0], y[0]) : Integer.compare(y[1], x[1]));
        List<Match> out = new ArrayList<>();
        int reached = -1;
        for (int[] s : spans) {
            if (s[0] < reached) continue;
            WordList.Entry e = entries.get(s[2]);
            out.add(new Match(t.from[s[0]], t.to[s[1] - 1], e.word(), e.tier()));
            reached = s[1];
        }
        return Collections.unmodifiableList(out);
    }

    // A unit that's a word of its own, one letter long (between breaks).
    private static boolean single(Text t, int i) {
        return (i == 0 || t.gapBefore[i]) && (i + 1 == t.n || t.gapBefore[i + 1]) && t.mask[i] != 0;
    }

    // ------------------------------------------------------------ matching a word

    // The words found in the word that's units a..b (exclusive): from each place in it, along the trie, each unit any
    // of the letters it stands for, a letter repeated (fuuuck) as one. whole: what's found, the whole word it's in
    // (but the symbols at the word's ends).
    private void scan(Text t, int a, int b, boolean whole, List<int[]> spans, List<WordList.Entry> entries) {
        // (What's of it but symbols at its ends, for the allow-list: "class!" is "class".)
        int ca = a, cb = b;
        while (ca < cb && t.symbol[ca] && !t.wild[ca]) ca++;
        while (cb > ca && t.symbol[cb - 1] && !t.wild[cb - 1]) cb--;
        String plain = null;
        for (int start = a; start < b; start++) {
            if (t.mask[start] == 0) continue;
            // Inside a stretched letter ("aaaa"), the walk from where it began finds all this one would: skip it.
            if (start > a && !t.wild[start] && !t.wild[start - 1] && t.mask[start - 1] != 0
                    && (t.mask[start] & ~t.mask[start - 1]) == 0) continue;
            var found = new ArrayList<int[]>();                         // {end, entry index}
            Walk w = new Walk(t, a, b, start, found, entries);
            w.go(words.root, start, -1, 0, 0, false);
            for (int[] f : found) {
                if (plain == null) plain = t.plain(ca, cb);
                if (allow.allows(plain)) continue;
                spans.add(whole ? new int[]{Math.min(start, ca), Math.max(f[0], cb), f[1]} : new int[]{start, f[0], f[1]});
            }
        }
    }

    // One walk along the trie from a place in a word (a..b): where each word it reaches ends.
    private static final class Walk {
        final Text t;
        final int a, b, start;
        final List<int[]> found;
        final List<WordList.Entry> entries;
        final Set<Long> seen = new HashSet<>();
        final boolean startsWord;

        Walk(Text t, int a, int b, int start, List<int[]> found, List<WordList.Entry> entries) {
            this.t = t; this.a = a; this.b = b; this.start = start; this.found = found; this.entries = entries;
            boolean ok = true;
            for (int i = a; i < start; i++) if (!t.symbol[i] || t.wild[i]) { ok = false; break; }
            startsWord = ok;                                            // (only symbols before it: "$hit", "@ss")
        }

        // At trie node n, unit i next; last: the letter just taken (-1 none); real, letters (not wildcards) taken;
        // total, units taken; letter: whether one of them was a letter (not a digit or symbol).
        void go(WordList.Node n, int i, int last, int real, int total, boolean letter) {
            // (A loop along a stretched letter, so that a long run of one doesn't recurse once a unit.)
            for (;; i++) {
                long key = ((long) n.id << 37) | ((long) i << 5) | (last + 1);   // (node, unit, letter just taken: each once)
                if (!seen.add(key)) return;
                if (n.ends != null && total > 0) for (WordList.Entry e : n.ends) if (fits(e, i, real, total, letter)) {
                    entries.add(e);
                    found.add(new int[]{i, entries.size() - 1});
                }
                if (i >= b) return;
                int m = t.mask[i];
                if (m == 0) return;
                boolean wild = t.wild[i], isLetter = !t.symbol[i];
                if (wild && total == 0) return;                             // (not from a wildcard: "*ss" isn't "ass")
                for (int bits = m; bits != 0; bits &= bits - 1) {
                    int k = Integer.numberOfTrailingZeros(bits);
                    WordList.Node next = n.next[k];
                    if (next != null) go(next, i + 1, k, real + (wild ? 0 : 1), total + 1, letter || isLetter);
                }
                // The letter just taken again: the same one, stretched (fuuuck, shiiit).
                if (last < 0 || wild || (m & (1 << last)) == 0) return;
                letter |= isLetter;
            }
        }

        // Whether a word reached, ending before unit `end`, is found there: where it may be in a word, and real enough
        // (a letter in it; more letters than wildcards, two at least).
        boolean fits(WordList.Entry e, int end, int real, int total, boolean letter) {
            if (!letter || real < 2 && total > real || real * 2 < total) return false;
            boolean endsWord = true;
            for (int i = end; i < b; i++) if (!t.symbol[i] || t.wild[i]) { endsWord = false; break; }
            return switch (e.kind()) {
                case WHOLE -> startsWord && endsWord;
                case START -> startsWord;
                case ANYWHERE -> true;
            };
        }
    }

    // ------------------------------------------------------------ the allow-list

    // Words never masked though they hold a listed one ("class", "Scunthorpe", "cocktail"): whole words, or with a
    // trailing *, words that start so; as plain letters, a repeated letter as one too.
    private static final class Allow {
        final Set<String> whole = new HashSet<>(), starts = new HashSet<>();

        void add(String written) {
            String w = written.strip().toLowerCase(Locale.ROOT);
            boolean prefix = w.endsWith("*");
            StringBuilder letters = new StringBuilder();
            for (int i = 0; i < w.length(); i++) { char c = w.charAt(i); if (c >= 'a' && c <= 'z') letters.append(c); }
            if (letters.isEmpty()) return;
            (prefix ? starts : whole).add(letters.toString());
        }

        boolean allows(String word) {
            if (whole.contains(word) || whole.contains(squeeze(word))) return true;
            for (String s : starts) if (word.startsWith(s)) return true;
            return false;
        }

        private static String squeeze(String w) {
            StringBuilder out = new StringBuilder(w.length());
            for (int i = 0; i < w.length(); i++) if (i == 0 || w.charAt(i) != w.charAt(i - 1)) out.append(w.charAt(i));
            return out.toString();
        }
    }

    // ------------------------------------------------------------ building one

    /** Builds a {@link Bleep}: the built-in list or none, its mild words or not, words of your own, and the mask. */
    public static final class Builder {
        private boolean standard, mild;
        private final List<String> added = new ArrayList<>(), allowed = new ArrayList<>();
        private char replacement = '#';
        private boolean wholeWords;

        private Builder() {}

        /** Starts from the built-in English list (its strong words; {@link #mild(boolean)} for its mild ones too). */
        public Builder standard() { standard = true; return this; }

        /** Whether the built-in list's mild words ({@code damn}, {@code hell}, {@code crap}) are masked too. */
        public Builder mild(boolean on) { mild = on; return this; }

        /**
         * Words of your own to mask. As a list writes them: {@code word} for the whole word only, {@code word*} for
         * words that start with it, {@code *word*} for it anywhere in a word.
         */
        public Builder add(String... words) { for (String w : words) if (w != null) added.add(w); return this; }

        /** Words never to mask, though they hold a listed word ({@code word}, or {@code word*} for words that start so). */
        public Builder allow(String... words) { for (String w : words) if (w != null) allowed.add(w); return this; }

        /** The character a masked word's characters become ({@code #} to begin with). */
        public Builder replacement(char c) { replacement = c; return this; }

        /**
         * Whether the whole word a match is in is masked ({@code fucking}: {@code #######}), or only what matched
         * ({@code ####ing}, to begin with). Punctuation at the word's ends is kept either way.
         */
        public Builder maskWholeWords(boolean on) { wholeWords = on; return this; }

        /** The filter. */
        public Bleep build() {
            WordList list = new WordList();
            Allow allow = new Allow();
            if (standard) {
                Set<Tier> tiers = mild ? EnumSet.allOf(Tier.class) : EnumSet.of(Tier.STRONG);
                try (InputStream w = resource("words.txt"); InputStream a = resource("allow.txt")) {
                    WordList.read(w, list, tiers);
                    for (String line : WordList.lines(a)) allow.add(line);
                } catch (IOException e) {
                    throw new UncheckedIOException("Bleep's built-in list couldn't be read", e);
                }
            }
            for (String w : added) list.add(w, Tier.STRONG);
            for (String w : allowed) allow.add(w);
            return new Bleep(list, allow, replacement, wholeWords);
        }

        private static InputStream resource(String name) throws IOException {
            InputStream in = Bleep.class.getResourceAsStream(name);
            if (in == null) throw new IOException(name + " is missing from Bleep's jar");
            return in;
        }
    }
}
