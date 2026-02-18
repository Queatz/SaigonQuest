package routes

import auth.JwtConfig
import com.auth0.jwt.exceptions.JWTVerificationException
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.consumeEach
import models.AiChatMessage
import models.AiChatClientEvent
import models.AiChatServerEvent
import models.AiChatSession
import models.AiRole
import repository.AiChatRepository
import repository.UserRepository
import services.DeepSeekClient
import utils.json

fun Routing.configureAiChatRoutes() {
    val users = UserRepository()
    val sessions = AiChatRepository()

    webSocket("/ws/ai/chat") {
        // 1) Authenticate like existing /ws route
        val token = call.parameters["token"].orEmpty()
        val user = if (token.isNotBlank()) {
            try {
                val decoded = JwtConfig.verifier.verify(token)
                val userId = decoded.getClaim("userId").asString()
                users.getById(userId)
            } catch (e: JWTVerificationException) {
                null
            }
        } else null

        if (user == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return@webSocket
        }

        val userId = user.key
        var sessionId: String? = null

        suspend fun sendError(msg: String) {
            val payload = json.encodeToString(
                AiChatServerEvent.Error.serializer(),
                AiChatServerEvent.Error(message = msg),
            )
            send(payload)
        }

        suspend fun sendAssistant(text: String) {
            val payload = json.encodeToString(
                AiChatServerEvent.AssistantMessage.serializer(),
                AiChatServerEvent.AssistantMessage(text = text),
            )
            send(payload)
        }

        incoming.consumeEach { frame ->
            if (frame !is Frame.Text) return@consumeEach
            val text = frame.readText()

            // Try each client event type
            val start = runCatching { json.decodeFromString(AiChatClientEvent.Start.serializer(), text) }.getOrNull()
            if (start != null) {
                // Create new session
                val initial = start.initialMessages.map {
                    AiChatMessage(role = it.role, content = it.content, createdAt = it.createdAt)
                }.toMutableList()

                // Ensure optional system message is first if provided
                val sys = start.system
                if (!sys.isNullOrBlank()) {
                    initial.add(0, AiChatMessage(role = AiRole.System, content = sys))
                }

                val created = AiChatSession(
                    userId = userId,
                    messages = initial,
                    isClosed = false,
                )
                val id = sessions.insert(created)
                sessionId = id

                val openedPayload = json.encodeToString(
                    AiChatServerEvent.SessionOpened.serializer(),
                    AiChatServerEvent.SessionOpened(sessionId = id),
                )
                send(openedPayload)

                // If last message is User, immediately answer
                val last = initial.lastOrNull()
                if (last?.role == AiRole.User) {
                    val systemPrompt = initial.firstOrNull { it.role == AiRole.System }?.content ?: "You are a helpful assistant."
                    val historyText = initial
                        .drop(1)
                        .joinToString("\n\n") { m ->
                            when (m.role) {
                                AiRole.User -> "User: ${m.content}"
                                AiRole.Assistant -> "Assistant: ${m.content}"
                                AiRole.System -> ""
                            }
                        }
                    val userPrompt = last.content
                    val assistant = DeepSeekClient.promptText(
                        systemPrompt = buildString {
                            appendLine(systemPrompt)
                            if (historyText.isNotBlank()) {
                                appendLine()
                                appendLine("Conversation so far:")
                                appendLine(historyText)
                            }
                            appendLine()
                            appendLine("Respond concisely to the latest user message.")
                        },
                        userPrompt = userPrompt,
                        temperature = 0.6,
                    )
                    if (!assistant.isNullOrBlank()) {
                        sendAssistant(assistant)
                        sessionId?.let { sid -> sessions.appendMessage(sid, AiChatMessage(role = AiRole.Assistant, content = assistant)) }
                    }
                }
                return@consumeEach
            }

            val userMsg = runCatching { json.decodeFromString(AiChatClientEvent.UserMessage.serializer(), text) }.getOrNull()
            if (userMsg != null) {
                val sid = sessionId
                if (sid == null) {
                    sendError("No open session")
                    return@consumeEach
                }
                // Save user message
                sessions.appendMessage(sid, AiChatMessage(role = AiRole.User, content = userMsg.text))

                // Build short context from last ~12 messages
                val sess = sessions.getById(sid) ?: return@consumeEach
                val ctx = sess.messages.takeLast(12)
                val systemPrompt = ctx.firstOrNull { it.role == AiRole.System }?.content ?: "You are a helpful assistant."
                val convo = ctx.filter { it.role != AiRole.System }
                val historyText = convo.dropLast(1).joinToString("\n\n") { m ->
                    when (m.role) {
                        AiRole.User -> "User: ${m.content}"
                        AiRole.Assistant -> "Assistant: ${m.content}"
                        else -> ""
                    }
                }
                val latestUser = userMsg.text

                val assistant = DeepSeekClient.promptText(
                    systemPrompt = buildString {
                        appendLine(systemPrompt)
                        if (historyText.isNotBlank()) {
                            appendLine()
                            appendLine("Conversation so far:")
                            appendLine(historyText)
                        }
                        appendLine()
                        appendLine("Respond concisely to the latest user message.")
                    },
                    userPrompt = latestUser,
                    temperature = 0.6,
                )

                if (assistant.isNullOrBlank()) {
                    sendError("Empty AI response")
                } else {
                    sendAssistant(assistant)
                    sessions.appendMessage(sid, AiChatMessage(role = AiRole.Assistant, content = assistant))
                }
                return@consumeEach
            }

            val end = runCatching { json.decodeFromString(AiChatClientEvent.End.serializer(), text) }.getOrNull()
            if (end != null) {
                try { close(CloseReason(CloseReason.Codes.NORMAL, "client end")) } catch (_: Throwable) {}
                return@consumeEach
            }
        }
    }
}
