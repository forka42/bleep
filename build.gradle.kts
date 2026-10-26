plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.forka42"
version = "0.1.0"

repositories { mavenCentral() }

java {
    withSourcesJar()
    withJavadocJar()
}

// Plain Java 17 (Android, GraalVM native images and every JVM since): no dependencies.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

// The docs say what each returns in their first sentence: no @param and @return lines for the sake of it.
tasks.javadoc { (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet") }

publishing {
    publications {
        create<MavenPublication>("bleep") {
            from(components["java"])
            pom {
                name = "Bleep"
                description = "A small, fast swear filter for Java, built for English game chat."
                url = "https://github.com/forka42/bleep"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/license/mit"
                    }
                }
            }
        }
    }
}
