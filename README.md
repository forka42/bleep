# Bleep

A small, fast swear filter for Java, built for English game chat.

Bleep finds swears, slurs and sexual terms in text and masks them, including when they're disguised (leetspeak,
look-alike letters, spacing, stretched letters, accents), without flagging innocent words that happen to contain one.

## Why

Java has no well-maintained, English-first profanity filter. The good ones are in Python and JavaScript, and the main
Java option is built around Chinese word lists. Bleep fills that gap for games and chat apps that run on the JVM.

## Features

- **Catches disguised words.**
  - Leetspeak and symbols: `sh1t`, `$hit`, `a$$`.
  - Look-alike letters from other alphabets: Cyrillic `ѕhit`, full-width `ｓｈｉｔ`.
  - Spaces and punctuation between letters: `f.u.c.k`, `f u c k`, `f-u-c-k`.
  - Stretched letters: `fuuuuck`.
  - Accents: `fück`.
- **Leaves innocent words alone.** `Scunthorpe`, `class`, `assassin`, `grass` and `cocktail` are not flagged. This is
  the "Scunthorpe problem", handled with word-boundary rules and an allow-list.
- **Masks only what it matched.** Spacing, punctuation and the rest of the message are kept as written. Or, if you
  prefer, the whole word a match is in (`fucking`: `#######` rather than `####ing`).
- **Tiers.** Strong words are on by default. Mild words (`damn`, `hell`, `crap`) are a separate tier you can switch on.
- **Your own words.** Add words, allow words, or replace the built-in list entirely.
- **Fast.** Text is normalised once and matched against a trie, with the work bounded even for long spam, so it can
  run on every chat message on a game server.
- **No dependencies.** Plain Java 17. Works on Android and in GraalVM native images.

## Installing

Bleep is built from this repository by [JitPack](https://jitpack.io). With Gradle:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.pingsocc:bleep:main-SNAPSHOT")   // or a release tag, or a commit hash
}
```

## Usage

```java
Bleep bleep = Bleep.standard();                 // the built-in English list

bleep.clean("what the f.u.c.k");                // "what the #######"
bleep.contains("sh1t happens");                 // true
bleep.contains("Scunthorpe United");            // false

for (Bleep.Match m : bleep.find(text)) {        // each match: where it is, and which word
    System.out.println(m.start() + ".." + m.end() + " " + m.word());
}
```

Customise it with the builder:

```java
Bleep bleep = Bleep.builder()
        .standard()                             // start from the built-in list
        .mild(true)                             // also mask mild words
        .add("customword")                      // words of your own
        .allow("assessment")                    // never flag these
        .replacement('*')                       // mask with * instead of #
        .maskWholeWords(true)                   // "fucking": "#######", not "####ing"
        .build();
```

A `Bleep` is immutable and thread-safe: build it once and share it.

## How it works

1. **Normalise.** Each character becomes the set of letters it could stand for: accents are stripped, look-alikes are
   folded (`ѕ` → `s`, `ｆ` → `f`), and leetspeak can mean more than one letter (`1` → `i` or `l`). Invisible characters
   such as zero-width spaces are dropped. Each one remembers where it came from in the original text.
2. **Match.** Each word is walked against a trie of the word list, trying every letter a character could be. Repeated
   letters are collapsed while matching, so `fuuuck` matches `fuck`. A `*` stands for any letter (`f*ck`), as long as
   most of the word is real letters. Runs of three or more single letters (`f u c k`, `f.u.c.k`) are joined and
   checked as one word.
3. **Check boundaries.** Each listed word says where it may match: as a whole word (`ass`, so not `class`), at the
   start of a word (`fuck*`, so `fucking`), or anywhere (`*shit*`, so `bullshit`). The allow-list is checked too, so
   `shiitake` and `Scunthorpe` stay clean.
4. **Mask.** The matched span in the original text is replaced, a mask character for each character, spaces kept.

## Word list

Bleep ships its own English word list, written for this project rather than copied, so its licensing is simple. It
lives in [`words.txt`](src/main/resources/io/github/pingsocc/bleep/words.txt), with the innocent words it would
otherwise catch in [`allow.txt`](src/main/resources/io/github/pingsocc/bleep/allow.txt). Words are grouped by tier:

| Tier | Default | Examples |
|---|---|---|
| Strong | On | Swears, slurs, sexual terms |
| Mild | Off | `damn`, `hell`, `crap` |

## Building

```sh
./gradlew build
```

This compiles Bleep and runs its tests: disguises, innocent words, masking, the builder and long spam.

## Licence

MIT, word lists included. See [LICENSE](LICENSE).
