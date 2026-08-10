plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    // A toolchain (rather than sourceCompatibility) pins the exact JDK used to
    // compile and test, independent of whatever JDK happens to run Gradle.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21.0.12"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass = "com.example.weather.Main"
}

// Gradle runs the app in a separate JVM, which does not inherit -D flags from the Gradle
// invocation. Forward the application's own config keys so `./gradlew run -Dcache.ttl.minutes=1`
// behaves the way `java -Dcache.ttl.minutes=1 -jar ...` does.
val configPrefixes = listOf("openmeteo.", "http.", "cache.", "forecast.", "history.")
tasks.named<JavaExec>("run") {
    System.getProperties().forEach { key, value ->
        val name = key.toString()
        if (configPrefixes.any { name.startsWith(it) }) {
            systemProperty(name, value.toString())
        }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.1")
    implementation("org.slf4j:slf4j-api:2.0.18")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Fat jar: `./gradlew shadowJar` -> build/libs/weather-app-<version>-all.jar
// JavaFX refuses to start from a class that extends Application when the
// runtime is on the classpath instead of the module path, so the fat jar uses
// the Launcher indirection instead of Main.
tasks.shadowJar {
    archiveClassifier = "all"
    // Let the service-file transformer see every copy of META-INF/services/* so the SLF4J and
    // Jackson provider files get merged rather than the first one winning.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes("Main-Class" to "com.example.weather.Launcher")
    }
    mergeServiceFiles()
}
