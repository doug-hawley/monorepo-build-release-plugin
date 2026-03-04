plugins {
   `kotlin-dsl`
   `java-gradle-plugin`
   `maven-publish`
   id("com.gradle.plugin-publish") version "2.0.0"
}

group = "io.github.doug-hawley"
version = "0.3.3" // x-release-please-version

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
}

// Configure source sets for unit, integration, and functional tests
sourceSets {
    val unitTest by creating {
        kotlin {
            srcDir("src/test/unit/kotlin")
        }
        resources {
            srcDir("src/test/unit/resources")
        }
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }

    val integrationTest by creating {
        kotlin {
            srcDir("src/test/integration/kotlin")
        }
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }

    val functionalTest by creating {
        kotlin {
            srcDir("src/test/functional/kotlin")
        }
        resources {
            srcDir("src/test/functional/resources")
        }
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

// Add dependencies for the test source sets
dependencies {
    // Unit test dependencies
    add("unitTestImplementation", "io.kotest:kotest-runner-junit5:5.9.1")
    add("unitTestImplementation", "io.kotest:kotest-assertions-core:5.9.1")
    add("unitTestImplementation", "io.kotest:kotest-property:5.9.1")
    add("unitTestImplementation", "io.kotest:kotest-framework-datatest:5.9.1")
    add("unitTestImplementation", "io.mockk:mockk:1.13.12")

    // Integration test dependencies
    add("integrationTestImplementation", "io.kotest:kotest-runner-junit5:5.9.1")
    add("integrationTestImplementation", "io.kotest:kotest-assertions-core:5.9.1")
    add("integrationTestImplementation", "io.mockk:mockk:1.13.12")

    // Functional test dependencies
    add("functionalTestImplementation", gradleTestKit())
    add("functionalTestImplementation", "io.kotest:kotest-runner-junit5:5.9.1")
    add("functionalTestImplementation", "io.kotest:kotest-assertions-core:5.9.1")
}

// Register unit test task
val unitTest by tasks.registering(Test::class) {
    description = "Runs unit tests"
    group = "verification"
    testClassesDirs = sourceSets["unitTest"].output.classesDirs
    classpath = sourceSets["unitTest"].runtimeClasspath
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}

// Register integration test task
val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests against a real git backend"
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    shouldRunAfter(unitTest)
}

// Register functional test task
val functionalTest by tasks.registering(Test::class) {
    description = "Runs functional tests"
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    shouldRunAfter(unitTest, integrationTest)
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

// Make check depend on all test types
tasks.named("check") {
    dependsOn(unitTest, integrationTest, functionalTest)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}

gradlePlugin {
    website.set("https://github.com/doug-hawley/monorepo-build-release-plugin")
    vcsUrl.set("https://github.com/doug-hawley/monorepo-build-release-plugin.git")

    plugins {
        register("monorepoBuildReleasePlugin") {
            id = "io.github.doug-hawley.monorepo-build-release-plugin"
            implementationClass = "io.github.doughawley.monorepo.MonorepoBuildReleasePlugin"
            displayName = "Monorepo Build & Release Plugin"
            description = "Selective change detection and per-project versioning for Gradle monorepos"
            tags.set(listOf("monorepo", "git", "ci", "release", "versioning"))
        }
    }
}

publishing {
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
