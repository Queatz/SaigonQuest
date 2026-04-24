package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import models.AiPromptHistory
import models.AiPromptRequest
import models.AiTtsRequest
import models.AiImageRequest
import models.AiImageResponse
import models.ErrorResponse
import models.MinigameGenerateRequest
import models.MinigameType
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

                call.respondText(text)
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

            post("/image") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val token = call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")

                val req = try { call.receive<AiImageRequest>() } catch (t: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid request", code = HttpStatusCode.BadRequest.value, details = t.message)
                    )
                }

                if (req.prompt.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Prompt cannot be empty", code = HttpStatusCode.BadRequest.value)
                    )
                }

                if (!AiManager.isConfigured()) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            message = "AI integration not configured",
                            code = HttpStatusCode.InternalServerError.value,
                        )
                    )
                }

                val imageUrl = try {
                    AiManager.client.generateImageUrl(req.prompt, req.size, token)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    return@post call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(message = e.message ?: "Image generation failed", code = HttpStatusCode.BadGateway.value)
                    )
                }

                if (imageUrl == null) {
                    return@post call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(message = "Failed to generate image URL", code = HttpStatusCode.BadGateway.value)
                    )
                }

                // Save to history
                val history = AiPromptHistory(
                    userId = userId,
                    prompt = req.prompt,
                    responsePreview = imageUrl,
                )
                runCatching { historyRepo.insert(history) }

                call.respond(AiImageResponse(imageUrl))
            }

            post("/tts") {
                val req = try { call.receive<AiTtsRequest>() } catch (t: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid request", code = HttpStatusCode.BadRequest.value, details = t.message)
                    )
                }

                val format = call.request.queryParameters["format"] ?: "pcm"
                println("[DEBUG_LOG] TTS Route hit for text: ${req.text.take(50)}..., format: $format")

                if (req.text.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Empty text", code = HttpStatusCode.BadRequest.value)
                    )
                }

                // Ensure TTS provider is configured
                if (!AiManager.isConfigured(AiManager.ttsClient)) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            message = "TTS provider not configured (missing ${AiManager.getApiKeyName(AiManager.ttsClient)})",
                            code = HttpStatusCode.InternalServerError.value,
                        )
                    )
                }

                val contentType = if (format == "pcm") ContentType("audio", "l16") else ContentType.Audio.MPEG
                call.respondBytesWriter(contentType) {
                    AiManager.ttsClient.ttsStream(req.text, format) { chunk ->
                        writeFully(chunk)
                        flush()
                    }
                }
            }

            post("/minigame/generate") {
                val req = try { call.receive<MinigameGenerateRequest>() } catch (t: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid request", code = HttpStatusCode.BadRequest.value, details = t.message)
                    )
                }

                if (!AiManager.isConfigured()) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(message = "AI integration not configured", code = HttpStatusCode.InternalServerError.value)
                    )
                }

                val sysPrompt = buildMinigameSystemPrompt(req.type)

                val text = AiManager.client.promptText(
                    systemPrompt = sysPrompt,
                    userPrompt = req.prompt,
                    temperature = 0.1,
                    json = true
                )

                if (text.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadGateway,
                        ErrorResponse(message = "Empty AI response", code = HttpStatusCode.BadGateway.value)
                    )
                }

                call.respondText(text, ContentType.Application.Json)
            }
        }
    }
}

private fun buildMinigameSystemPrompt(type: MinigameType): String {
    return buildString {
        appendLine("You are a minigame content generator for Saigon.Quest.")
        appendLine("Generate a JSON object containing an 'items' array of MinigameItem objects based on the user's prompt for the $type minigame type.")
        appendLine()
        appendLine("### MinigameItem JSON Structure:")
        appendLine("{")
        appendLine("  \"id\": \"string (unique identifier)\",")
        appendLine("  \"text\": \"string (main content)\",")
        appendLine("  \"translation\": \"string (optional Vietnamese translation)\",")
        appendLine("  \"image\": null,")
        appendLine("  \"audio\": null,")
        appendLine("  \"definition\": \"string (optional extra info/clue)\",")
        appendLine("  \"matchId\": \"string (optional, used for pairing/grouping)\",")
        appendLine("  \"hotspot\": null")
        appendLine("}")
        appendLine()
        appendLine("### CRITICAL RULES:")
        appendLine("1. All 'id' and 'matchId' values MUST be strings, even if they contain numbers.")
        appendLine("2. Use null for 'image', 'audio', and 'hotspot'.")
        appendLine("3. Respond ONLY with the JSON object. Do not include any text before or after the JSON.")
        appendLine()
        appendLine("Rules for $type:")
        when (type) {
            MinigameType.Flashcards -> {
                appendLine("- 'text': Word in English.")
                appendLine("- 'translation': Word in Vietnamese.")
                appendLine("- 'definition': Brief definition or example.")
            }
            MinigameType.MatchGame -> {
                appendLine("- 'text': Word/phrase.")
                appendLine("- 'matchId': Common ID for paired items.")
            }
            MinigameType.Scenario -> {
                appendLine("- 'text': Name of object to find.")
                appendLine("- 'hotspot': Leave null.")
            }
            MinigameType.Sequencing -> {
                appendLine("- 'text': Step or ingredient name.")
                appendLine("- Generate items in the CORRECT LOGICAL ORDER (e.g., first step first, last step last).")
            }
            MinigameType.Categorization -> {
                appendLine("- 'text': Item to sort.")
                appendLine("- 'matchId': The Category name (e.g., 'Fruit', 'Drink', 'Verb').")
                appendLine("- Use 2-3 different matchIds across 6-9 items.")
            }
            MinigameType.Identify -> {
                appendLine("- 'text': The name of the object to identify.")
                appendLine("- Generate 4-6 distinct items that could be confused with each other (e.g., different types of food).")
            }
            MinigameType.Spelling -> {
                appendLine("- 'text': Word to spell in English.")
                appendLine("- 'translation': Word in Vietnamese (used as a clue).")
                appendLine("- 'definition': Brief definition (used as a clue).")
            }
            MinigameType.Story -> {
                appendLine("- 'text': The story text for a step OR the text for a choice.")
                appendLine("- 'id': Provide a unique ID for each story step (e.g., 'step1', 'step2').")
                appendLine("- 'matchId': For choices, set to the ID of the story step they belong to.")
                appendLine("- 'definition': For choices, set to 'correct' if it is the right option to advance the story. For story steps, leave empty.")
                appendLine("- Group items by step: a story step followed by EXACTLY 4 choices, then the next story step and its choices.")
                appendLine("- Ensure exactly one choice per step is marked as 'correct' in 'definition'.")
            }
        }
        if (type == MinigameType.Story) {
            appendLine("- Generate 3-4 steps, with each step followed by 4 choices (15-20 items total).")
        } else {
            appendLine("- Generate 5-10 items.")
        }
        appendLine("- Respond ONLY with a JSON object: { \"items\": [...] }")
    }
}

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
