package main

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import utils.json

val httpClient = HttpClient {
    install(ContentNegotiation) { json(json) }
}

