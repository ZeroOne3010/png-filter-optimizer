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
    implementation("ar.com.hjg:pngj:2.1.0")
    implementation("it.unimi.dsi:fastutil:8.5.18")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
