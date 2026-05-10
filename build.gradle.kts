plugins {
    application
    `java-library`
    jacoco
    id("me.champeau.jmh") version "0.7.3"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

application {
    mainClass.set("io.github.zeroone3010.pngfilteropt.Main")
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.github.leonbloy:pngj:2.1.0")
    implementation("it.unimi.dsi:fastutil:8.5.18")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.9.3")
}

tasks.test {
    useJUnitPlatform()
}
