plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.20"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

kotlin {
    jvm()

    js(IR) {
        outputModuleName = "saigon-quest-shared"
        browser {
            testTask {
                useKarma {
                    useFirefox()
                }
            }
        }
    }
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.arangodb:arangodb-java-driver:7.22.1")
                implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
            }
        }
    }
}
