plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ktor.plugin") version "3.3.1"
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
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":shared"))
    
    // Ktor server
    implementation("io.ktor:ktor-server-core:3.3.1")
    implementation("io.ktor:ktor-server-netty:3.3.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.1")
    implementation("io.ktor:ktor-server-cors:3.3.1")
    implementation("io.ktor:ktor-server-websockets:3.3.1")
    
    // Ktor client (for external HTTP calls e.g., DeepSeek)
    implementation("io.ktor:ktor-client-core:3.3.1")
    implementation("io.ktor:ktor-client-cio:3.3.1")
    implementation("io.ktor:ktor-client-websockets:3.3.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.1")
    
    // Ktor JWT Authentication
    implementation("io.ktor:ktor-server-auth:3.3.1")
    implementation("io.ktor:ktor-server-auth-jwt:3.3.1")
    
    // Auth0 JWT Library
    implementation("com.auth0:java-jwt:4.4.0")
    
    // ArangoDB - Latest version as required
    implementation("com.arangodb:arangodb-java-driver:7.22.1")
    
    // Jackson modules for date/time handling
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("org.glassfish:jakarta.json:2.0.1")

    // KotlinX Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.20")

    // Web Push (VAPID + HTTP ECE)
    implementation("nl.martijndwars:web-push:5.1.2")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // Apache HttpComponents
    implementation("org.apache.httpcomponents:httpcore:4.4.16")
}

kotlin {
    jvmToolchain(21)
}
