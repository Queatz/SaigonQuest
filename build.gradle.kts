import org.codehaus.groovy.ast.tools.GeneralUtils.args

plugins {
    kotlin("multiplatform") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("org.jetbrains.compose") version "1.8.1"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

// Alias 'run' to start the Kotlin/JS webpack dev server
tasks.register("run") {
    group = "application"
    description = "Runs the Kotlin/JS webpack dev server"
    dependsOn("jsBrowserDevelopmentRun")
}

defaultTasks("run")

kotlin {
    js(IR) {
        browser {
            runTask {
                args("-t")
                devServer = devServer.copy(port = 4040, open = true)
            }
            commonWebpackConfig {
                outputFileName = "main.js"
                cssSupport {
                    enabled = true
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.html.core)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.components.resources)

                implementation("app.softwork:routing-compose:0.4.0")
                implementation(npm("marked", "15.0.7"))

                // Ktor client
                implementation("io.ktor:ktor-client-core:3.1.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }

        val jsMain by getting
    }
}
