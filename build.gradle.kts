
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose")
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

// Prepare service-worker.js from ':sw' for frontend: copies to the correct folder for dev or prod
tasks.register<Copy>("prepareDevServiceWorker") {
    dependsOn(":sw:jsBrowserDevelopmentExecutableDistribution")

    val variant = "developmentExecutable"
    val swFile = rootProject.layout.projectDirectory
        .file("sw/build/dist/js/$variant/service-worker.js")

    val destDir = layout.buildDirectory.dir("dist/js/$variant")
    val destDir2 = layout.buildDirectory.dir("processedResources/js/main")

    // Defer existence check to execution time so the dependsOn task can produce it first
    doFirst {
        if (!swFile.asFile.exists()) error("Could not find service-worker.js at $swFile")
    }

    // Copy to both destinations
    into(destDir)
    from(swFile)

    into(destDir2)
    from(swFile)

    doLast {
        println("Copied service-worker.js to $destDir and $destDir2 (mode=dev)")
    }
}

tasks.register<Copy>("prepareProdServiceWorker") {
    dependsOn(":sw:jsBrowserDistribution")
    val variant = "productionExecutable"
    val swFile = rootProject.layout.projectDirectory
        .file("sw/build/dist/js/$variant/service-worker.js")

    val destDir = layout.buildDirectory.dir("dist/js/$variant")

    // Defer existence check to execution time so the dependsOn task can produce it first
    doFirst {
        if (!swFile.asFile.exists()) error("Could not find service-worker.js at $swFile")
    }

    into(destDir)
    from(swFile)

    doLast {
        println("Copied service-worker.js to $destDir (mode=prod)")
    }
}

// Convenience task: build SW and start frontend dev server
tasks.register("devApp") {
    group = "application"
    description = "Builds the service worker and starts the frontend dev server"
    dependsOn("prepareDevServiceWorker", ":jsBrowserDevelopmentRun")
}

// Convenience task: build SW and start frontend dev server
tasks.register("prodApp") {
    group = "application"
    description = "Builds the service worker and starts the frontend dev server"
    dependsOn("prepareProdServiceWorker", ":jsBrowserDistribution")
}

kotlin {
    js(IR) {
        browser {
            runTask {
                devServerProperty.set(devServerProperty.get().copy(port = 4080, open = true))
            }
            commonWebpackConfig {
                outputFileName = "saigon-quest.js"
                cssSupport {
                    enabled = true
                }
            }
            testTask {
                useKarma {
                    useFirefox()
                }
            }
        }
        binaries.executable()
    }
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.html.core)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                // Removed compose.components.resources as it's causing runtime errors and not directly used

                implementation("app.softwork:routing-compose:0.5.0")
                implementation(npm("marked", "16.4.1"))
                implementation(npm("qr", "0.5.2"))
                implementation(npm("@babylonjs/core", "8.32.2"))
                implementation(npm("@babylonjs/materials", "8.32.2"))
                implementation(npm("@babylonjs/loaders", "8.32.2"))
                implementation(npm("shell-quote", "1.8.3")) // force version
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:2025.10.10")

                // Ktor client
                implementation("io.ktor:ktor-client-core:3.3.1")
                implementation("io.ktor:ktor-client-content-negotiation:3.3.1")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                
                // Add shared module dependency
                implementation(project(":shared"))

                implementation(kotlin("test"))
            }
        }

        val jsMain by getting
    }
}

// Ensure correct task order between service worker preparation and JS compile sync tasks
// This fixes Gradle's implicit dependency validation by making the compile sync task
// depend on the task that provides files in build/processedResources/js/main.
tasks.named("jsDevelopmentExecutableCompileSync") {
    dependsOn("prepareDevServiceWorker")
}

// Also wire the production variant for consistency
tasks.named("jsProductionExecutableCompileSync") {
    dependsOn("prepareProdServiceWorker")
}
