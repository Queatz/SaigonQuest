pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    // Plugin versions are centralized here so every module can apply plugins without
    // re-declaring versions (avoids classpath version conflicts in multi-project builds).
    // Keep these in sync with [versions] / [plugins] in gradle/libs.versions.toml.
    val kotlin = "2.4.10"
    val compose = "1.11.0-alpha02"
    val ktor = "3.4.1"

    plugins {
        kotlin("multiplatform") version kotlin
        kotlin("jvm") version kotlin
        kotlin("plugin.serialization") version kotlin
        id("org.jetbrains.kotlin.plugin.compose") version kotlin
        id("org.jetbrains.compose") version compose
        id("io.ktor.plugin") version ktor
    }
}

rootProject.name = "saigon-quest"

include(":shared")
include(":backend")
include(":sw")
include(":content")
