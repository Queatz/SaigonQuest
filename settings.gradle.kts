pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    plugins {
        kotlin("multiplatform") version "2.3.20"
        id("org.jetbrains.compose") version "1.11.0-alpha02"
    }
}

rootProject.name = "saigon-quest"

include(":shared")
include(":backend")
include(":sw")
include(":content")
