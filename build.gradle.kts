import org.codehaus.groovy.ast.tools.GeneralUtils.args

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
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
    args("-t")
}

defaultTasks("run")

// Prepare service-worker.js from ':sw' for frontend: copies to the correct folder for dev or prod
tasks.register<Copy>("prepareDevServiceWorker") {
    dependsOn(":sw:jsBrowserDevelopmentWebpack")
    // Compute dev/prod at execution time; avoid capturing other Project instances
    val variant = "developmentExecutable"
    val swFile = rootProject.layout.projectDirectory
        .file("sw/build/dist/js/$variant/service-worker.js")
        if (!swFile.asFile.exists()) error("Could not find service-worker.js at $swFile")

    val destDir = layout.buildDirectory.dir("dist/js/$variant")
    from(swFile)
    into(destDir)
    val destDir2 = layout.buildDirectory.dir("processedResources/js/main")
    from(swFile)
    into(destDir2)
    println("Copied service-worker.js to $destDir and $destDir2 (mode=dev)")
}

tasks.register<Copy>("prepareProdServiceWorker") {
    dependsOn(":sw:jsBrowserDistribution")
    val variant = "productionExecutable"
    val swFile = rootProject.layout.projectDirectory
        .file("sw/build/dist/js/$variant/service-worker.js")
        if (!swFile.asFile.exists()) error("Could not find service-worker.js at $swFile")
    val destDir = layout.buildDirectory.dir("dist/js/$variant")
    from(swFile)
    into(destDir)
    println("Copied service-worker.js to $destDir (mode=prod)")
}

// Convenience task: build SW and start frontend dev server
tasks.register("devApp") {
    group = "application"
    description = "Builds the service worker and starts the frontend dev server"
    dependsOn("prepareDevServiceWorker", "jsBrowserDevelopmentRun")
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
                args("-t")
                devServer = devServer.copy(port = 4080, open = true)
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

                implementation("app.softwork:routing-compose:0.4.0")
                implementation(npm("marked", "15.0.7"))
                implementation(npm("@paulmillr/qr", "0.2.0"))
                implementation(npm("@babylonjs/core", "8.27.0"))
                implementation(npm("@babylonjs/materials", "8.27.0"))
                implementation(npm("@babylonjs/loaders", "8.27.0"))
                implementation(npm("shell-quote", "1.8.3")) // force version
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:2025.7.15")

                // Ktor client
                implementation("io.ktor:ktor-client-core:3.2.2")
                implementation("io.ktor:ktor-client-content-negotiation:3.2.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.2")
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
