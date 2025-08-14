plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "service-worker.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                // Typed web API wrappers (ServiceWorker, Notifications, Push)
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:2025.7.13")
            }
        }
    }
}
