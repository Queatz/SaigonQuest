plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvmToolchain(21)

    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "service-worker.js"
            }
        }
        binaries.executable()
    }
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // Typed web API wrappers (ServiceWorker, Notifications, Push)
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:2026.3.16")
            }
        }
    }
}

