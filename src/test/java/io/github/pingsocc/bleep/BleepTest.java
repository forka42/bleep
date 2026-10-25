package io.github.pingsocc.bleep;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BleepTest {
    static final Bleep BLEEP = Bleep.standard();

    // ------------------------------------------------------------ found

    @ParameterizedTest
    @ValueSource(strings = {"fuck", "FUCK", "Fuck you", "what the fuck", "fucking", "motherfucker", "fucker", "shit", "bullshit",
            "shitty", "bitch", "sonofabitch", "cunt", "ass", "you ass", "asshole", "dickhead", "bastards", "whore", "slut"})
    void plainWords(String text) { assertTrue(BLEEP.contains(text), text); }

    @ParameterizedTest
    @ValueSource(strings = {"sh1t", "5h1t", "$hit", "a$$", "@ss", "a55hole", "f4ggot", "b1tch", "fuk", "phuck", "fvck", "d1ck", "c0ck"})
    void leetspeakAndSymbols(String text) { assertTrue(BLEEP.contains(text), text); }

    @ParameterizedTest
    @ValueSource(strings = {
            "ѕhit",                  // Cyrillic ѕ
            "fuсk",                  // Cyrillic с
            "ｆｕｃｋ",               // full width
            "𝐟𝐮𝐜𝐤",   // mathematical bold 𝐟𝐮𝐜𝐤
            "fück", "shït", "fúck",                          // accents, composed and apart
            "fu​ck"})                                          // a zero-width space in it
    void lookAlikesAccentsAndInvisibles(String text) { assertTrue(BLEEP.contains(text), text); }

    @ParameterizedTest
    @ValueSource(strings = {"f u c k", "f.u.c.k", "f-u-c-k", "s h i t", "what the f u c k man", "a s s"})
    void spacedOut(String text) { assertTrue(BLEEP.contains(text), text); }

    @ParameterizedTest
    @ValueSource(strings = {"fuuuuuck", "shiiiit", "fuckkkk", "asssss", "biiiitch"})
    void stretched(String text) { assertTrue(BLEEP.contains(text), text); }

    @ParameterizedTest
    @ValueSource(strings = {"f*ck", "sh*t", "f**k", "b*tch"})
    void wildcards(String text) { assertTrue(BLEEP.contains(text), text); }

    @ParameterizedTest
    @ValueSource(strings = {"shit!", "ass?", "(fuck)", "\"bitch\"", "fuck's sake", "ass."})
    void punctuationAround(String text) { assertTrue(BLEEP.contains(text), text); }

    // ------------------------------------------------------------ left alone

    @ParameterizedTest
    @ValueSource(strings = {"class", "classic", "pass", "bass", "grass", "assassin", "assessment", "Scunthorpe", "cocktail",
            "cockpit", "peacock", "Hancock", "Dickens", "therapist", "grapes", "drape", "shiitake mushrooms", "Pakistan",
            "spice", "titanic", "cumulative", "document", "scrap", "hello", "shell", "Nigeria", "Niger", "snigger",
            "niggle", "rapeseed oil", "pedometer", "Wankel engine", "retardant", "cocoon", "raccoon", "button", "analysis",
            "I passed the test", "Mitsubishi", "assume", "glass", "embarrass"})
    void innocentWords(String text) { assertFalse(BLEEP.contains(text), text + ": " + BLEEP.find(text)); }

    @ParameterizedTest
    @ValueSource(strings = {"455", "a**", "****", "1337", "$$$", "a s", "I am a big fan", "s**", "!!!"})
    void notEnoughToBeAWord(String text) { assertFalse(BLEEP.contains(text), text + ": " + BLEEP.find(text)); }

    @Test void mildWordsOnlyOnRequest() {
        assertFalse(BLEEP.contains("damn it"), "mild: off by default");
        assertFalse(BLEEP.contains("go to hell"));
        Bleep mild = Bleep.builder().standard().mild(true).build();
        assertTrue(mild.contains("damn it"));
        assertTrue(mild.contains("go to hell"));
        assertFalse(mild.contains("hello there"));
        assertFalse(mild.contains("seashell"));
    }

    // ------------------------------------------------------------ masking

    @Test void masksOnlyWhatItFound() {
        assertEquals("what the #######", BLEEP.clean("what the f.u.c.k"));
        assertEquals("well #### happens", BLEEP.clean("well sh1t happens"));
        assertEquals("you ### and you ###!", BLEEP.clean("you ass and you a$$!"));
        assertEquals("# # # #", BLEEP.clean("f u c k"), "the spaces kept");
        assertEquals("a nice class", BLEEP.clean("a nice class"), "nothing to mask: the same text");
        assertEquals("#### 🙂", BLEEP.clean("fück 🙂"));
        assertEquals("####", BLEEP.clean("ｆｕｃｋ"), "a mask a character, not a char");
    }

    @Test void whereEachIs() {
        List<Bleep.Match> found = BLEEP.find("oh shit, a bitch");
        assertEquals(2, found.size());
        assertEquals(new Bleep.Match(3, 7, "shit", Bleep.Tier.STRONG), found.get(0));
        assertEquals("bitch", found.get(1).word());
        assertEquals("bitch", "oh shit, a bitch".substring(found.get(1).start(), found.get(1).end()));
    }

    @Test void nothingInNothing() {
        assertEquals(List.of(), BLEEP.find(""));
        assertEquals(List.of(), BLEEP.find(null));
        assertEquals("", BLEEP.clean(""));
    }

    // ------------------------------------------------------------ your own

    @Test void yourOwnWordsAndAllowList() {
        Bleep b = Bleep.builder().standard().add("noob*", "*badword*").allow("assmann").replacement('*').build();
        assertEquals("you ****ie", b.clean("you n00bie"), "only what matched");
        assertTrue(b.contains("superbadwordy"));
        assertFalse(b.contains("Assmann"), "allowed");
        assertEquals("****", b.clean("shit"));
        Bleep empty = Bleep.builder().add("rude").build();
        assertFalse(empty.contains("fuck"), "nothing but its own");
        assertTrue(empty.contains("so RUDE"));
    }

    @Test void wholeWordsOnRequest() {
        Bleep whole = Bleep.builder().standard().maskWholeWords(true).build();
        assertEquals("####ing hell", BLEEP.clean("fucking hell"), "only what matched, to begin with");
        assertEquals("####### hell", whole.clean("fucking hell"));
        assertEquals("oh ########!", whole.clean("oh bullshit!"), "the punctuation kept");
        assertEquals("what the #######", whole.clean("what the f.u.c.k"), "spaced out: as before");
        assertEquals("a nice class", whole.clean("a nice class"));
        assertEquals(List.of(new Bleep.Match(0, 7, "fuck", Bleep.Tier.STRONG)), whole.find("fucking"));
    }

    @Test void longSpamIsQuick() {
        String spam = "a".repeat(20000) + " " + "fuu".repeat(5000) + "ck " + "s h i t ".repeat(2000);
        long start = System.nanoTime();
        assertTrue(BLEEP.contains(spam));
        assertTrue((System.nanoTime() - start) / 1e9 < 2, "quick enough");
    }

    @Test void sharedBetweenThreads() throws Exception {
        Thread[] threads = new Thread[8];
        boolean[] ok = new boolean[threads.length];
        for (int i = 0; i < threads.length; i++) {
            int k = i;
            threads[i] = new Thread(() -> {
                boolean all = true;
                for (int j = 0; j < 500; j++) all &= BLEEP.contains("sh1t " + j) && !BLEEP.contains("class " + j);
                ok[k] = all;
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        for (boolean b : ok) assertTrue(b);
    }
}
