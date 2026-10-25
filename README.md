# Bleep

A small, fast swear filter for Java. It sees through leetspeak, look-alike letters, spacing and stretched letters
(`sh1t`, `ｆｕｃｋ`, `f.u.c.k`, `fuuuck`) and leaves innocent words like `class` and `Scunthorpe` alone.

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { implementation("com.github.pingsocc:bleep:main-SNAPSHOT") }
```

```java
Bleep bleep = Bleep.standard();
bleep.clean("what the f.u.c.k");   // "what the #######"
bleep.contains("sh1t happens");    // true
```

Customise it with `Bleep.builder()`: `mild`, `add`, `allow`, `replacement` and `maskWholeWords`.

MIT licensed.
