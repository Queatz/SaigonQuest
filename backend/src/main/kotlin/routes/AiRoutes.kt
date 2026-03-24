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
import services.AiManager

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
                if (!AiManager.isConfigured()) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            message = "AI integration not configured (missing ${AiManager.getApiKeyName()})",
                            code = HttpStatusCode.InternalServerError.value,
                        )
                    )
                }

                val sysPrompt = buildSystemPrompt(req.docContents, req.caretStart, req.caretEnd, req.docName)

                val text = AiManager.client.promptText(
                    systemPrompt = sysPrompt,
                    userPrompt = req.prompt,
                    temperature = 0.5,
                )

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
    val docName: String? = null,
)

@Serializable
data class AiPromptResponse(
    val text: String,
)

private fun buildSystemPrompt(contents: String, caretStart: Int, caretEnd: Int, docName: String?): String {
    val caretSection = if (caretStart != caretEnd) {
        "FROM OFFSET $caretStart TO OFFSET $caretEnd"
    } else {
        "AT OFFSET $caretStart"
    }
    return buildString {
        appendLine("You are helping a user edit a document.")
        appendLine()
        appendLine("# CRITICAL INSTRUCTIONS")
        appendLine()
        appendLine(" - Output the markdown-supported text to be inserted into the document, replacing the user's current selection.")
        appendLine(" - Document contents are provided for context ONLY - DO NOT INCLUDE IN YOUR OUTPUT.")
        appendLine(" - Output ONLY the exact text to be inserted.")
        appendLine()
        appendLine("# USER SELECTION (IN TOTAL CHARACTER OFFSETS, INCLUDING ALL MARKUP AND LINE BREAKS)")
        appendLine()
        appendLine(caretSection)
        appendLine()
        if (caretStart != caretEnd) {
            appendLine("# SELECTION CONTENTS")
            appendLine()
            appendLine("```")
            appendLine(contents.substring(caretStart, caretEnd))
            appendLine("```")
            appendLine()
        }
        if (!docName.isNullOrBlank()) {
            appendLine("# DOCUMENT TITLE")
            appendLine()
            appendLine(docName)
            appendLine()
        }
        appendLine("# DOCUMENT CONTENTS (ALL TEXT BELOW THIS LINE) -----------")
        appendLine(contents)
    }
}
