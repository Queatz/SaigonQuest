plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

kotlin {
    jvmToolchain(21)

    jvm()

    js {
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
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.arangodb.java.driver)
                implementation(libs.jackson.datatype.jsr310)
                implementation(libs.jackson.module.kotlin)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(npm("emoji-regex", libs.versions.emoji.regex.get()))
            }
        }
    }
}
