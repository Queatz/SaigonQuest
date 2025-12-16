plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ktor.plugin") version "3.3.3"
    application
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":shared"))

    // Ktor server
    implementation("io.ktor:ktor-server-core:3.3.3")
    implementation("io.ktor:ktor-server-netty:3.3.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
    implementation("io.ktor:ktor-server-cors:3.3.3")
    implementation("io.ktor:ktor-server-compression:3.3.3")
    implementation("io.ktor:ktor-server-caching-headers:3.3.3")

    // Ktor client for calling API backend
    implementation("io.ktor:ktor-client-core:3.3.3")
    implementation("io.ktor:ktor-client-cio:3.3.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")

    // ArangoDB
    implementation("com.arangodb:arangodb-java-driver:7.22.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("org.glassfish:jakarta.json:2.0.1")

    // KotlinX
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.20")
}

kotlin {
    jvmToolchain(21)
}
