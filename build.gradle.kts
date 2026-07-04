import java.time.Duration

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "io.github.danlewis783"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Gradle 9 requires Java 17 to run, so that is the plugin's bytecode floor.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website = "https://github.com/danlewis783/jpackage-plugin"
    vcsUrl = "https://github.com/danlewis783/jpackage-plugin.git"
    plugins {
        create("jpackage") {
            id = "io.github.danlewis783.jpackage"
            implementationClass = "io.github.danlewis783.jpackage.JPackagePlugin"
            displayName = "jpackage plugin"
            description = "Packages Java applications as jpackage app images, zips, and native installers."
            tags = listOf("jpackage", "packaging", "installer", "msi", "native-image", "distribution")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // The functional test runs jpackage/jlink; give it headroom.
    timeout.set(Duration.ofMinutes(15))
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
