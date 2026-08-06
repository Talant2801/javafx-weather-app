plugins {
    // Lets Gradle download the Java 21 toolchain automatically if the machine
    // does not have one installed, so `./gradlew run` works on a clean checkout.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "weather-app"
