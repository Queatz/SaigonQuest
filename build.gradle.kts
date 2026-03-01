
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
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

// Copy Babylon Draco decoder assets (configuration-cache safe)
// 1) Copy into processed resources so webpack/dev server can serve at /babylon/Draco/*
tasks.register<Copy>("prepareDracoAssetsResources") {
    dependsOn("kotlinNpmInstall")
    from(layout.buildDirectory.dir("js/node_modules/@babylonjs/core/assets/Draco")) {
        include(
            "draco_wasm_wrapper_gltf.js",
            "draco_decoder_gltf.wasm",
            "draco_decoder_gltf.js",
        )
    }
    into(layout.buildDirectory.dir("processedResources/js/main/babylon/Draco"))
}

// 2) Copy to dev dist so the dev server can serve them directly
tasks.register<Copy>("prepareDracoAssetsDevDist") {
    dependsOn("kotlinNpmInstall")
    from(layout.buildDirectory.dir("js/node_modules/@babylonjs/core/assets/Draco")) {
        include(
            "draco_wasm_wrapper_gltf.js",
            "draco_decoder_gltf.wasm",
            "draco_decoder_gltf.js",
        )
    }
    into(layout.buildDirectory.dir("dist/js/developmentExecutable/babylon/Draco"))
}

// 3) Copy to prod dist for production bundle
tasks.register<Copy>("prepareDracoAssetsProdDist") {
    dependsOn("kotlinNpmInstall")
    from(layout.buildDirectory.dir("js/node_modules/@babylonjs/core/assets/Draco")) {
        include(
            "draco_wasm_wrapper_gltf.js",
            "draco_decoder_gltf.wasm",
            "draco_decoder_gltf.js",
        )
    }
    into(layout.buildDirectory.dir("dist/js/productionExecutable/babylon/Draco"))
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
    jvmToolchain(21)

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

                implementation("app.softwork:routing-compose:0.5.0")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:2025.11.5")

                implementation(npm("marked", "17.0.1"))
                implementation(npm("qr", "0.5.2"))
                implementation(npm("@babylonjs/core", "8.49.6"))
                implementation(npm("@babylonjs/materials", "8.49.6"))
                implementation(npm("@babylonjs/loaders", "8.49.6"))
                implementation(npm("babylonjs-gltf2interface", "8.49.6"))
                implementation(npm("shell-quote", "1.8.3")) // force version

                // Ktor client
                implementation("io.ktor:ktor-client-core:3.3.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
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
    dependsOn(
        "prepareDevServiceWorker",
        "prepareDracoAssetsResources",
        "prepareDracoAssetsDevDist",
    )
}

// Also wire the production variant for consistency
tasks.named("jsProductionExecutableCompileSync") {
    dependsOn(
        "prepareProdServiceWorker",
        "prepareDracoAssetsResources",
        "prepareDracoAssetsProdDist",
    )
}



// Ensure Draco assets are prepared before packaging JS jar
tasks.named("jsJar") {
    dependsOn("prepareDracoAssetsResources")
}
