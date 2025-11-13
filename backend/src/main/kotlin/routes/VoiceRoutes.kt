package routes

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import utils.json
import org.slf4j.LoggerFactory
import models.voice.VoiceAudioChunk

private const val model = "gpt-4o-realtime-preview"
private const val transcribeModel = "gpt-4o-mini-transcribe"

fun Routing.configureVoiceRoutes() {
    val log = LoggerFactory.getLogger("VoiceRoutes")
    webSocket("/ws/voice") {
        // OpenAI key
        val apiKey = System.getenv("OPENAI_API_KEY") ?: System.getProperty("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) {
            try {
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "OpenAI key not configured"))
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return@webSocket
        }

        // Client for OpenAI Realtime
        val client = HttpClient(CIO) {
            install(ClientWebSockets)
            install(ContentNegotiation) { json(json) }
        }

        // Model optimized for streaming ASR; request a transcription session
        val openAiUrl = "wss://api.openai.com/v1/realtime?intent=transcription"

        log.info("/ws/voice: connecting to OpenAI realtime: $openAiUrl")

        // Bridge channel for browser frames with ack logging
        val frameCh = kotlinx.coroutines.channels.Channel<Frame>(capacity = 64)
        var browserBinaryCount = 0
        val fromBrowserJob = launch {
            try {
                this@webSocket.incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Binary -> {
                            val bytes = frame.readBytes()
                            val size = bytes.size
                            browserBinaryCount++
                            if (browserBinaryCount <= 3) log.info("/ws/voice: browser -> server Binary $size bytes (sample #$browserBinaryCount)")
//                            val ack = buildJsonObject {
//                                put("type", JsonPrimitive("ack"))
//                                put(
//                                "ft",
//                                JsonPrimitive("binary")
//                            ); put("len", JsonPrimitive(size))
//                            }.toString()
//                            runCatching { this@webSocket.send(Frame.Text(ack)) }
                            frameCh.send(Frame.Binary(true, bytes))
                        }

                        is Frame.Text -> {
                            val text = frame.readText()
//                            browserTextCount++
//                            if (browserTextCount <= 3) log.info("/ws/voice: browser -> server Text: ${text.take(120)}")
//                            val ack = buildJsonObject {
//                                put("type", JsonPrimitive("ack")); put(
//                                "ft",
//                                JsonPrimitive("text")
//                            ); put("len", JsonPrimitive(text.length))
//                            }.toString()
//                            runCatching { this@webSocket.send(Frame.Text(ack)) }
                            frameCh.send(Frame.Text(text))
                        }

                        else -> {}
                    }
                }
            } finally {
                frameCh.close()
                log.info("/ws/voice: browser closed; totals: binary=$browserBinaryCount")
            }
        }

        client.webSocket(
            urlString = openAiUrl,
            request = {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("OpenAI-Beta", "realtime=v1")
            }
        ) openAi@{
            // Track session readiness for gating audio until the server applies our transcription settings
            val sessionReady = kotlinx.coroutines.CompletableDeferred<Unit>()
            var seenSessionType: String? = null

            // Configure transcription session (no model responses)
            runCatching {
                val sessionUpdate = buildJsonObject {
                    put("type", JsonPrimitive("transcription_session.update"))
                    putJsonObject("session") {
                        put("input_audio_format", JsonPrimitive("pcm16"))
                        putJsonObject("input_audio_transcription") {
                            put("model", JsonPrimitive(transcribeModel))
                            //put("prompt", JsonPrimitive("Transcribe exactly the user provided audio. Expect Vietnamese and/or English speech but detect other languages as needed. Totally ignore silence (do not output any text other than transcribed audio ever.)"))
                            put("prompt", JsonPrimitive(""))
                        }
                        putJsonObject("turn_detection") {
                            put("type", JsonPrimitive("server_vad"))
                            put("threshold", JsonPrimitive(0.3))
                            put("prefix_padding_ms", JsonPrimitive(100))
                            put("silence_duration_ms", JsonPrimitive(100))
                        }
                    }
                }.toString()
                log.info("/ws/voice: sending transcription_session.update to OpenAI (format=pcm16@24k, transcription.model=$transcribeModel)")
                this@openAi.send(Frame.Text(sessionUpdate))
            }
            // From browser -> OpenAI
            val toOpenAi = launch {
                // Wait briefly until the server acknowledges our transcription session settings
                runCatching {
                    kotlinx.coroutines.withTimeout(1000) { sessionReady.await() }
                    log.info("/ws/voice: sessionReady -> proceeding to send audio")
                }.onFailure {
                    log.warn("/ws/voice: session not confirmed as transcription within timeout; proceeding anyway")
                }
                for (frame in frameCh) {
                    when (frame) {
                        is Frame.Binary -> {
                            val audioBytes = frame.readBytes()
                            val size = audioBytes.size
                            if (size == 0) continue
                            log.info("/ws/voice: got binary chunk from client: $size bytes")

                            // Forward raw PCM16 mono 24k as base64; rely on server VAD to commit turns
                            val base64 = java.util.Base64.getEncoder().encodeToString(audioBytes)
                            val appendEvent = buildJsonObject {
                                put("type", JsonPrimitive("input_audio_buffer.append"))
                                put("audio", JsonPrimitive(base64))
                            }
                            val evStr = appendEvent.toString()
                            log.debug("/ws/voice -> OpenAI: input_audio_buffer.append (len=${evStr.length})")
                            this@openAi.send(Frame.Text(evStr))
                        }

                        is Frame.Text -> {
                            val txt = frame.readText()
                            // Control message
                            val ctrl =
                                runCatching { json.decodeFromString(VoiceControlMessage.serializer(), txt) }.getOrNull()
                            if (ctrl?.type == "start") {
                                log.info("/ws/voice: start received")
                                continue
                            } else if (ctrl?.type == "stop") {
                                // Force a commit to flush any pending audio; with server VAD this is usually not required
                                val commit = buildJsonObject { put("type", JsonPrimitive("input_audio_buffer.commit")) }
                                log.info("/ws/voice -> OpenAI: input_audio_buffer.commit (on stop)")
                                this@openAi.send(Frame.Text(commit.toString()))
                                continue
                            }
                            // audio chunk in text?
                            val audio =
                                runCatching { json.decodeFromString(VoiceAudioChunk.serializer(), txt) }.getOrNull()
                            if (audio?.type == "audio" && audio.b64.isNotBlank()) {
                                val audioBytes = try {
                                    java.util.Base64.getDecoder().decode(audio.b64)
                                } catch (_: Throwable) {
                                    byteArrayOf()
                                }
                                val size = audioBytes.size
                                if (size > 0) {
                                    log.info("/ws/voice: got base64 audio chunk from client: $size bytes")
                                    val base64 = java.util.Base64.getEncoder().encodeToString(audioBytes)
                                    val appendEvent = buildJsonObject {
                                        put("type", JsonPrimitive("input_audio_buffer.append"))
                                        put("audio", JsonPrimitive(base64))
                                    }
                                    val evStr = appendEvent.toString()
                                    log.debug("/ws/voice -> OpenAI: input_audio_buffer.append (len=${evStr.length})")
                                    this@openAi.send(Frame.Text(evStr))
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }

            // From OpenAI -> browser
            val toBrowser = launch {
                // Transcription-only session: do not forward assistant response events
                val transcriptionOnly = true
                for (frame in this@openAi.incoming) {
                    val txt = (frame as? Frame.Text)?.readText() ?: continue

                    // Debug log incoming OpenAI event (truncated)
                    runCatching { log.info("/ws/voice <- OpenAI: ${txt.take(1000)}") }

                    // Observe session events to confirm transcription mode before sending audio
                    runCatching {
                        val ev = json.decodeFromString(OpenAiSessionEventCreated.serializer(), txt)
                        if (ev.type == "session.created") {
                            val st = ev.session?.type
                            if (st != null) {
                                seenSessionType = st
                                log.info("/ws/voice: session.created -> type=$st")
                                if (st == "transcription" && !sessionReady.isCompleted) sessionReady.complete(Unit)
                            }
                        }
                    }
                    runCatching {
                        val ev = json.decodeFromString(OpenAiSessionEventUpdated.serializer(), txt)
                        if (ev.type == "session.updated") {
                            val st = ev.session?.type
                            if (st != null) {
                                seenSessionType = st
                                log.info("/ws/voice: session.updated -> type=$st")
                                if (st == "transcription" && !sessionReady.isCompleted) sessionReady.complete(Unit)
                            }
                        }
                    }
                    // New realtime transcription session events
                    runCatching {
                        val ev = json.decodeFromString(OpenAiEventType.serializer(), txt)
                        if (ev.type == "transcription_session.created" || ev.type == "transcription_session.updated") {
                            seenSessionType = "transcription"
                            if (!sessionReady.isCompleted) sessionReady.complete(Unit)
                            log.info("/ws/voice: ${ev.type} -> gating ready")
                        }
                    }

                    // Primary handlers for transcription sessions
                    // A) conversation.item.input_audio_transcription.delta
                    runCatching {
                        val ev = json.decodeFromString(OpenAiConvItemInputAudioTranscriptionDelta.serializer(), txt)
                        if (ev.type == "conversation.item.input_audio_transcription.delta" && !ev.delta.isNullOrBlank()) {
                            val out = VoiceTranscriptMessage(type = "partial", text = ev.delta)
                            val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                            this@webSocket.send(Frame.Text(s))
                        }
                    }
                    // B) conversation.item.input_audio_transcription.completed
                    runCatching {
                        val ev = json.decodeFromString(OpenAiConvItemInputAudioTranscriptionCompleted.serializer(), txt)
                        if (ev.type == "conversation.item.input_audio_transcription.completed" && !ev.transcript.isNullOrBlank()) {
                            val out = VoiceTranscriptMessage(type = "final", text = ev.transcript)
                            val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                            this@webSocket.send(Frame.Text(s))
                        }
                    }

                    // C) item { type: "item", item: "..." } (Azure blog sample)
                    runCatching {
                        val ev = json.decodeFromString(OpenAiItemString.serializer(), txt)
                        if (ev.type == "item" && !ev.item.isNullOrBlank()) {
                            val out = VoiceTranscriptMessage(type = "final", text = ev.item)
                            val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                            this@webSocket.send(Frame.Text(s))
                        }
                    }

                    // Fallbacks: handle older transcript-only events
                    // 1) transcript.delta { type, delta }
                    runCatching {
                        val ev = json.decodeFromString(OpenAiTranscriptDelta.serializer(), txt)
                        if (ev.type == "transcript.delta" && !ev.delta.isNullOrBlank()) {
                            val out = VoiceTranscriptMessage(type = "partial", text = ev.delta)
                            val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                            this@webSocket.send(Frame.Text(s))
                        }
                    }
                    // 2) transcript.completed { type, text }
                    runCatching {
                        val ev = json.decodeFromString(OpenAiTranscriptCompleted.serializer(), txt)
                        if (ev.type == "transcript.completed" && !ev.text.isNullOrBlank()) {
                            val out = VoiceTranscriptMessage(type = "final", text = ev.text)
                            val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                            this@webSocket.send(Frame.Text(s))
                        }
                    }

                    if (!transcriptionOnly) {
                        // Legacy assistant-related fallbacks (disabled in transcription-only mode)
                        // 3) response.audio_transcript.delta/done (older assistant audio flow)
                        runCatching {
                            val ev = json.decodeFromString(OpenAiResponseAudioTranscriptDelta.serializer(), txt)
                            if (ev.type == "response.audio_transcript.delta" && !ev.delta.isNullOrBlank()) {
                                val out = VoiceTranscriptMessage(type = "partial", text = ev.delta)
                                val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                                this@webSocket.send(Frame.Text(s))
                            }
                        }
                        runCatching {
                            val ev = json.decodeFromString(OpenAiResponseAudioTranscriptDone.serializer(), txt)
                            if (ev.type == "response.audio_transcript.done" && !ev.transcript.isNullOrBlank()) {
                                val out = VoiceTranscriptMessage(type = "final", text = ev.transcript)
                                val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                                this@webSocket.send(Frame.Text(s))
                            }
                        }
                        // 4) response.delta/completed/done fallbacks
                        runCatching {
                            val ev = json.decodeFromString(OpenAiResponseDelta.serializer(), txt)
                            if (ev.type == "response.delta") {
                                val t = ev.delta?.transcript ?: ev.delta?.output_text
                                if (!t.isNullOrBlank()) {
                                    val out = VoiceTranscriptMessage(type = "partial", text = t)
                                    val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                                    this@webSocket.send(Frame.Text(s))
                                }
                            }
                        }
                        runCatching {
                            val ev = json.decodeFromString(OpenAiResponseCompleted.serializer(), txt)
                            if (ev.type == "response.completed" || ev.type == "response.done") {
                                val t = ev.output_text ?: ev.text
                                if (!t.isNullOrBlank()) {
                                    val out = VoiceTranscriptMessage(type = "final", text = t)
                                    val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                                    this@webSocket.send(Frame.Text(s))
                                }
                            }
                        }
                        // 5) conversation.item.created fallback (prefer transcript)
                        runCatching {
                            val ev = json.decodeFromString(OpenAiConversationItemCreated.serializer(), txt)
                            if (ev.type == "conversation.item.created") {
                                val contentBlocks = ev.item?.content.orEmpty()
                                var text: String? = null
                                for (c in contentBlocks) {
                                    if (!c.transcript.isNullOrBlank()) {
                                        text = c.transcript; break
                                    }
                                }
                                if (text.isNullOrBlank()) {
                                    for (c in contentBlocks) {
                                        if (c.type == "output_text" && !c.text.isNullOrBlank()) {
                                            text = c.text; break
                                        }
                                    }
                                }
                                if (text.isNullOrBlank()) {
                                    for (c in contentBlocks) {
                                        if (c.type == "input_text" && !c.text.isNullOrBlank()) {
                                            text = c.text; break
                                        }
                                    }
                                }
                                if (!text.isNullOrBlank()) {
                                    val out = VoiceTranscriptMessage(type = "final", text = text)
                                    val s = json.encodeToString(VoiceTranscriptMessage.serializer(), out)
                                    this@webSocket.send(Frame.Text(s))
                                }
                            }
                        }
                    }
                }
            }

            try {
                toOpenAi.join()
            } finally {
                toBrowser.cancel()
                try {
                    this@openAi.close(CloseReason(CloseReason.Codes.NORMAL, "Closed"))
                } catch (_: Throwable) {
                }
            }
        }
    }
}

@Serializable
private data class VoiceControlMessage(
    val type: String,
)

@Serializable
private data class VoiceTranscriptMessage(
    val type: String, // "partial" | "final"
    val text: String,
)

// OpenAI realtime event shapes (subset)
@Serializable
private data class OpenAiTranscriptDelta(
    val type: String,
    val delta: String? = null,
)

@Serializable
private data class OpenAiTranscriptCompleted(
    val type: String,
    val text: String? = null,
)

@Serializable
private data class OpenAiResponseDelta(
    val type: String,
    val delta: OpenAiResponseDeltaBody? = null,
)

@Serializable
private data class OpenAiResponseDeltaBody(
    val transcript: String? = null,
    val output_text: String? = null,
)

@Serializable
private data class OpenAiResponseCompleted(
    val type: String,
    val output_text: String? = null,
    val text: String? = null,
)


@Serializable
private data class OpenAiConversationItemCreated(
    val type: String,
    val item: OpenAiConversationItem? = null,
    val event_id: String? = null,
)

@Serializable
private data class OpenAiConversationItem(
    val type: String,
    val role: String? = null,
    val content: List<OpenAiConversationContent> = emptyList(),
)

@Serializable
private data class OpenAiConversationContent(
    val type: String,
    val text: String? = null,
    val transcript: String? = null,
)

@Serializable
private data class OpenAiResponseAudioTranscriptDelta(
    val type: String,
    val delta: String? = null,
)

@Serializable
private data class OpenAiResponseAudioTranscriptDone(
    val type: String,
    val transcript: String? = null,
)

@Serializable
private data class OpenAiResponseContentPartDone(
    val type: String,
    val part: OpenAiResponseContentPart? = null,
)

@Serializable
private data class OpenAiResponseContentPart(
    val type: String,
    val text: String? = null,
    val transcript: String? = null,
)

// Transcription session events
@Serializable
private data class OpenAiConvItemInputAudioTranscriptionDelta(
    val type: String,
    val item_id: String? = null,
    val content_index: Int? = null,
    val delta: String? = null,
)

@Serializable
private data class OpenAiConvItemInputAudioTranscriptionCompleted(
    val type: String,
    val item_id: String? = null,
    val content_index: Int? = null,
    val transcript: String? = null,
)

// Session events for gating
@Serializable
private data class OpenAiSession(
    val type: String? = null,
)

@Serializable
private data class OpenAiSessionEventCreated(
    val type: String,
    val session: OpenAiSession? = null,
)

@Serializable
private data class OpenAiSessionEventUpdated(
    val type: String,
    val session: OpenAiSession? = null,
)


@Serializable
private data class OpenAiEventType(
    val type: String,
)

@Serializable
private data class OpenAiItemString(
    val type: String,
    val item: String? = null,
)
