package utils

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import models.ContentUpload
import java.io.File

object AssetUploader {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(utils.json)
        }
    }

    private val contentServerUrl = (System.getenv("CONTENT_SERVER_URL") ?: System.getProperty("CONTENT_SERVER_URL") ?: "http://0.0.0.0:8090").trimEnd('/')

    /**
     * Upload a file to the content server and return the public URL.
     */
    suspend fun upload(file: File, token: String): String? {
        return try {
            val response: HttpResponse = client.post("$contentServerUrl/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("API_USER_BEARER_TOKEN", token)
                            append(
                                key = "file",
                                value = file.readBytes(),
                                headers = Headers.build {
                                    val contentType = ContentType.defaultForFilePath(file.path)
                                    append(HttpHeaders.ContentType, contentType.toString())
                                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                }
                            )
                        }
                    )
                )
            }

            if (response.status == HttpStatusCode.Created) {
                val body: ContentUpload = response.body()
                // Ensure the path starts with a slash
                val path = if (body.uploadedFilePath.startsWith("/")) body.uploadedFilePath else "/${body.uploadedFilePath}"
                "$contentServerUrl$path"
            } else {
                println("[ERROR] Asset upload failed with status ${response.status}: ${response.bodyAsText()}")
                null
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}
