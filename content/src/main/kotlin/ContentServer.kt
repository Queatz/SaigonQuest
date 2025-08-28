import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.http.content.staticFiles
import io.ktor.server.application.call
import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.utils.io.jvm.javaio.toInputStream
import models.ContentUpload
import models.ValidateTokenResponse
import repository.UploadRepository
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.server.routing.Routing
import kotlinx.serialization.Serializable
import models.HealthResponse
import utils.json
import java.io.File
import java.util.UUID

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) {
        json(json)
    }

    install(CORS) { anyHost() }

    install(Compression) { gzip() }

    val staticDir = File("static").apply { mkdirs() }
    val repo = UploadRepository()

    routing {
        // Health check
        route("/") {
            get {
                call.respond(HealthResponse(alive = true))
            }
        }

        // Static files
        route("/static") {
            intercept(io.ktor.server.application.ApplicationCallPipeline.Call) {
                call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            }
            staticFiles("", staticDir)
        }

        // Upload endpoint
        post("/upload") {
            val apiBase = System.getenv("API_SERVER_URL")?.trimEnd('/')
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "Missing API_SERVER_URL env var")

            val multipart = call.receiveMultipart()
            var bearerToken: String? = null
            var originalName: String? = null
            var savedFile: File? = null
            var tooLarge = false
            val MAX_FILE_BYTES = 100L * 1024L * 1024L // 100 MB

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "API_USER_BEARER_TOKEN") bearerToken = part.value
                    }
                    is PartData.FileItem -> {
                        originalName = part.originalFileName ?: "upload.bin"
                        val sanitized = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        val target = File(staticDir, "${UUID.randomUUID()}_${sanitized}")
                        withContext(Dispatchers.IO) {
                            val channel = part.provider()
                            val input = channel.toInputStream()
                            input.use { inputStream ->
                                target.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var total = 0L
                                    while (true) {
                                        val read = inputStream.read(buffer)
                                        if (read <= 0) break
                                        total += read
                                        if (total > MAX_FILE_BYTES) {
                                            tooLarge = true
                                            break
                                        }
                                        output.write(buffer, 0, read)
                                    }
                                }
                            }
                            if (tooLarge) {
                                // Remove partial file
                                try { target.delete() } catch (_: Throwable) {}
                            }
                        }
                        if (!tooLarge) {
                            savedFile = target
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (tooLarge) {
                return@post call.respond(HttpStatusCode.BadRequest, "File too large (max 100 MB)")
            }

            val token = bearerToken?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing API_USER_BEARER_TOKEN")

            if (savedFile == null || originalName == null) {
                return@post call.respond(HttpStatusCode.BadRequest, "Missing file upload")
            }

            // Validate token with API backend
            val userId = try {
                AuthClient(apiBase).validate(token)
            } catch (e: Throwable) {
                e.printStackTrace()
                return@post call.respond(HttpStatusCode.Unauthorized, "Token validation failed")
            }
            if (userId == null) return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token")

            val record = ContentUpload(
                userId = userId,
                originalFileName = originalName,
                uploadedFilePath = "/static/${savedFile.name}"
            )

            val saved = repo.save(record)

            // Also notify backend to record in the user's media library
            try {
                BackendClient(apiBase).createMedia(token, CreateUserMediaRequest(
                    type = "Image",
                    path = saved.uploadedFilePath,
                    originalFileName = saved.originalFileName
                ))
            } catch (_: Throwable) {
                // Do not fail the upload if media insert fails
            }

            call.respond(HttpStatusCode.Created, saved)
        }
    }
}

private class AuthClient(private val baseUrl: String) {
    private val client = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun validate(token: String): String? {
        val res = client.get("$baseUrl/auth/validate") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (res.status != HttpStatusCode.OK) return null
        val body: ValidateTokenResponse = res.body()
        return if (body.valid) body.userId else null
    }
}

@Serializable
private data class CreateUserMediaRequest(
    val type: String,
    val path: String,
    val originalFileName: String
)

private class BackendClient(private val baseUrl: String) {
    private val client = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun createMedia(bearerToken: String, body: CreateUserMediaRequest) {
        client.post("$baseUrl/media") {
            headers { append(HttpHeaders.Authorization, "Bearer $bearerToken") }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
}
