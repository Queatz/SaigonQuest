package main.api

import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.InternalAPI
import js.buffer.ArrayBuffer
import js.typedarrays.Int8Array
import kotlinx.coroutines.CompletableDeferred
import models.ContentUpload
import org.w3c.files.File
import org.w3c.files.FileReader

object ContentApi {
    private val client get() = ApiClient.httpClient

    fun imageUrlFor(path: String): String = ContentApiConstants.CONTENT_BASE_URL + path

    // Convert a browser File to ByteArray for multipart upload
    private suspend fun fileToByteArray(file: File): ByteArray {
        val reader = FileReader()
        val result = CompletableDeferred<ByteArray>()
        reader.onload = {
            result.complete(Int8Array(reader.result as ArrayBuffer).unsafeCast<ByteArray>())
        }
        reader.onerror = {
            result.completeExceptionally(RuntimeException("Failed to read file: $it"))
        }
        reader.readAsArrayBuffer(file)
        return result.await()
    }

    @OptIn(InternalAPI::class)
    suspend fun upload(file: File, bearerToken: String): ContentUpload {
        val bytes = fileToByteArray(file)
        val filename = file.name.ifBlank { "upload" }
        val contentType = file.type.ifBlank { "application/octet-stream" }
        return upload(bytes, filename, contentType, bearerToken)
    }

    @OptIn(InternalAPI::class)
    suspend fun upload(
        bytes: ByteArray,
        filename: String,
        contentType: String,
        bearerToken: String
    ): ContentUpload {
        val url = ContentApiConstants.CONTENT_BASE_URL + "/upload"
        val response = client.post(url) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("API_USER_BEARER_TOKEN", bearerToken)
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, contentType)
                                append(HttpHeaders.ContentDisposition, "filename=$filename")
                            }
                        )
                    }
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Upload failed: ${response.status}")
        }
        return response.body()
    }
}
