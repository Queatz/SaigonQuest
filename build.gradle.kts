plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
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

// Prepare service-worker.js from ':sw' for the frontend dist output (dev or prod).
// Only copy into dist — not processedResources — so we don't collide with
// js*ExecutableCompileSync inputs (that dependency path also deadlocks via :sw webpack).
tasks.register<Copy>("prepareDevServiceWorker") {
    dependsOn(":sw:jsBrowserDevelopmentExecutableDistribution")

    val variant = "developmentExecutable"
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
        println("Copied service-worker.js to $destDir (mode=dev)")
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

    js {
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
        nodejs {
            testTask {
                useMocha()
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

                implementation(libs.routing.compose)
                implementation(libs.kotlin.browser)

                implementation(npm("marked", libs.versions.marked.get()))
                implementation(npm("qr", libs.versions.qr.get()))
                implementation(npm("emoji-regex", libs.versions.emoji.regex.get()))
                implementation(npm("@babylonjs/core", libs.versions.babylonjs.get()))
                implementation(npm("@babylonjs/materials", libs.versions.babylonjs.get()))
                implementation(npm("@babylonjs/loaders", libs.versions.babylonjs.get()))
                implementation(npm("babylonjs-gltf2interface", libs.versions.babylonjs.get()))
                implementation(npm("shell-quote", libs.versions.shell.quote.get())) // force version
                implementation(devNpm("baseline-browser-mapping", libs.versions.baseline.browser.mapping.get()))

                // Ktor client
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                // Add shared module dependency
                implementation(project(":shared"))

                implementation(kotlin("test"))
            }
        }

        val jsMain by getting
    }
}

// Draco assets land in processedResources / dist and must be ready before compile-sync.
// Service worker prep intentionally is NOT wired here: it depends on the :sw webpack
// pipeline, which would create a finalizer-group deadlock with this compile-sync task.
tasks.named("jsDevelopmentExecutableCompileSync") {
    dependsOn(
        "prepareDracoAssetsResources",
        "prepareDracoAssetsDevDist",
    )
}

tasks.named("jsProductionExecutableCompileSync") {
    dependsOn(
        "prepareDracoAssetsResources",
        "prepareDracoAssetsProdDist",
    )
}

// Copy the built service worker into the frontend dist before webpack/run/distribution.
tasks.named("jsBrowserDevelopmentWebpack") {
    dependsOn("prepareDevServiceWorker")
}
tasks.named("jsBrowserDevelopmentRun") {
    dependsOn("prepareDevServiceWorker")
}
tasks.named("jsBrowserProductionWebpack") {
    dependsOn("prepareProdServiceWorker")
}
tasks.named("jsBrowserDistribution") {
    dependsOn("prepareProdServiceWorker")
}

// Ensure Draco assets are prepared before packaging JS jar
tasks.named("jsJar") {
    dependsOn("prepareDracoAssetsResources")
}
