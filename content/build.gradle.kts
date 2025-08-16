plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.0"
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

dependencies {
    implementation(project(":shared"))

    // Ktor server
    implementation("io.ktor:ktor-server-core:3.2.2")
    implementation("io.ktor:ktor-server-netty:3.2.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.2")
    implementation("io.ktor:ktor-server-cors:3.2.2")
    implementation("io.ktor:ktor-server-compression:3.2.2")
    implementation("io.ktor:ktor-server-caching-headers:3.2.2")

    // Ktor client for calling API backend
    implementation("io.ktor:ktor-client-core:3.2.2")
    implementation("io.ktor:ktor-client-cio:3.2.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.2.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.2")

    // ArangoDB
    implementation("com.arangodb:arangodb-java-driver:7.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")

    // KotlinX
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.13")
}

kotlin {
    jvmToolchain(21)
}
