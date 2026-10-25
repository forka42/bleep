package io.github.forka42.bleep;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Words to find, in a trie by their letters, each with where it may be found in a word:
 * <ul>
 *   <li>{@code word}: only as a whole word ({@code ass}: not in {@code class} or {@code assassin});</li>
 *   <li>{@code word*}: at a word's start, whatever follows ({@code fuck*}: {@code fucking}, {@code fucker});</li>
 *   <li>{@code *word*}: anywhere in a word ({@code *shit*}: {@code bullshit}).</li>
 * </ul>
 */
final class WordList {
    enum Kind { WHOLE, START, ANYWHERE }

    /** A word: its letters, where it may be found, and its tier. */
    record Entry(String word, Kind kind, Bleep.Tier tier) {}

    /** A node of the trie: a child for each next letter, and the words that end here. */
    static final class Node {
        final int id;                                                   // (its number in the list: a walk's states)
        final Node[] next = new Node[26];
        List<Entry> ends;
        Node(int id) { this.id = id; }
    }

    private int nodes;
    final Node root = new Node(nodes++);
    private int size;

    int size() { return size; }

    /** Adds a word as the list writes it (a trailing *, a leading and trailing *); false if it has nothing to find. */
    boolean add(String written, Bleep.Tier tier) {
        String w = written.trim().toLowerCase(Locale.ROOT);
        Kind kind = Kind.WHOLE;
        if (w.startsWith("*") && w.endsWith("*") && w.length() > 2) { kind = Kind.ANYWHERE; w = w.substring(1, w.length() - 1); }
        else if (w.endsWith("*")) { kind = Kind.START; w = w.substring(0, w.length() - 1); }
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < w.length(); i++) { char c = w.charAt(i); if (c >= 'a' && c <= 'z') letters.append(c); }
        if (letters.isEmpty()) return false;
        Node n = root;
        for (int i = 0; i < letters.length(); i++) {
            int k = letters.charAt(i) - 'a';
            if (n.next[k] == null) n.next[k] = new Node(nodes++);
            n = n.next[k];
        }
        if (n.ends == null) n.ends = new ArrayList<>(1);
        n.ends.add(new Entry(letters.toString(), kind, tier));
        size++;
        return true;
    }

    /**
     * Reads a list: a word a line; "# ..." a comment; "[strong]", "[mild]" the tier of those after it (strong to begin
     * with). Only the tiers wanted are kept.
     */
    static void read(InputStream in, WordList into, java.util.Set<Bleep.Tier> tiers) throws IOException {
        Bleep.Tier tier = Bleep.Tier.STRONG;
        try (var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) { tier = Bleep.Tier.valueOf(line.substring(1, line.length() - 1).strip().toUpperCase(Locale.ROOT)); continue; }
                if (tiers.contains(tier)) into.add(line, tier);
            }
        }
    }

    /** The lines of a plain list (the allow-list): a word a line, "# ..." a comment. */
    static List<String> lines(InputStream in) throws IOException {
        List<String> out = new ArrayList<>();
        try (var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
            }
        }
        return out;
    }
}
