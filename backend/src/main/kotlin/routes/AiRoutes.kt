package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.Serializable
import models.AiPromptHistory
import models.ErrorResponse
import repository.AiHistoryRepository
import services.DeepSeekClient

fun Routing.configureAiRoutes() {
    val historyRepo = AiHistoryRepository()

    route("/ai") {
        authenticate {
            post("/prompt") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                val req = try { call.receive<AiPromptRequest>() } catch (t: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid request", code = HttpStatusCode.BadRequest.value, details = t.message)
                    )
                }

                // Preflight: ensure API key is configured to avoid opaque empty responses
                val apiKey = System.getenv("DEEPSEEK_API_KEY") ?: System.getProperty("DEEPSEEK_API_KEY")
                if (apiKey.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            message = "AI integration not configured (missing DEEPSEEK_API_KEY)",
                            code = HttpStatusCode.InternalServerError.value,
                        )
                    )
                }

                val sysPrompt = buildSystemPrompt(req.docContents, req.caretStart, req.caretEnd)

                // 2-minute timeout per requirements
                val text = try {
                    withTimeout(120_000) {
                        DeepSeekClient.promptText(
                            systemPrompt = sysPrompt,
                            userPrompt = req.prompt,
                            logSession = null
                        )
                    }
                } catch (t: TimeoutCancellationException) {
                    return@post call.respond(
                        HttpStatusCode.GatewayTimeout,
                        ErrorResponse(message = "AI request timed out", code = HttpStatusCode.GatewayTimeout.value, details = t.message)
                    )
                } catch (t: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "AI request failed", code = HttpStatusCode.InternalServerError.value, details = t.message)
                    )
                }

                if (text.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(message = "Empty AI response", code = HttpStatusCode.BadGateway.value)
                    )
                }

                // Save history (preview limited)
                val preview = text.take(200)
                val entry = AiPromptHistory(
                    userId = userId,
                    prompt = req.prompt,
                    responsePreview = preview,
                    docId = req.docId,
                    docLength = req.docContents.length,
                    caretStart = req.caretStart,
                    caretEnd = req.caretEnd,
                )
                runCatching { historyRepo.insert(entry) }

                call.respond(AiPromptResponse(text = text))
            }

            get("/history") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val items = runCatching { historyRepo.listByUserId(userId, limit.coerceIn(1, 200)) }
                    .getOrElse {
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(message = it.message ?: "Error", code = HttpStatusCode.InternalServerError.value)
                        )
                    }
                call.respond(items)
            }
        }
    }
}

@Serializable
data class AiPromptRequest(
    val prompt: String,
    val docContents: String,
    val caretStart: Int,
    val caretEnd: Int,
    val docId: String? = null,
)

@Serializable
data class AiPromptResponse(
    val text: String,
)

private fun buildSystemPrompt(contents: String, caretStart: Int, caretEnd: Int): String {
    fun toLineCol(idx: Int): Pair<Int, Int> {
        var line = 1
        var col = 1
        var i = 0
        while (i < idx && i < contents.length) {
            if (contents[i] == '\n') {
                line += 1
                col = 1
            } else {
                col += 1
            }
            i += 1
        }
        return line to col
    }
    val (sLine, sCol) = toLineCol(caretStart.coerceIn(0, contents.length))
    val (eLine, eCol) = toLineCol(caretEnd.coerceIn(0, contents.length))
    val caretSection = if (caretStart != caretEnd) {
        "line $sLine, position $sCol UNTIL line $eLine, position $eCol"
    } else {
        "line $sLine, position $sCol"
    }
    return buildString {
        appendLine("You are helping a user edit a document.")
        appendLine()
        appendLine("# INSTRUCTIONS")
        appendLine("Return the markdown supported text to be inserted into the document (replacing any selection)")
        appendLine()
        appendLine("# CARET POSITION")
        appendLine(caretSection)
        appendLine()
        appendLine("# DOCUMENT CONTENTS")
        appendLine()
        appendLine(contents)
    }
}
