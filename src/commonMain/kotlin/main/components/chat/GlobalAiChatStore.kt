package main.components.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import lib.AudioBufferSourceNode
import lib.AudioContext
import main.api.AiApi
import main.api.ApiClient
import main.api.ApiConstants
import main.api.UserApi
import main.components.audio.MusicManager
import models.AiChatClientEvent
import models.AiChatMessageDto
import models.AiChatServerEvent
import models.AiRole
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

object GlobalAiChatStore {
    private val scope = MainScope()

    var isOpen by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var messages by mutableStateOf(listOf<AiChatMessageDto>())
        private set

    var input by mutableStateOf("")
        private set

    var isFullscreen by mutableStateOf(false)
        private set

    var stopVoiceRequest by mutableStateOf(false)
        private set

    var speakingIndex by mutableStateOf(-1)
        private set

    var isSpeechPaused by mutableStateOf(false)
        private set

    var isSpeechLoading by mutableStateOf(false)
        private set

    private var speechJob: Job? = null
    private var isFetchingSpeech = false

    private var sessionId: String? = null
    private var socket: WebSocket? = null
    var initialMessageCount by mutableStateOf(0)
        private set

    fun open(
        system: String? = null,
        initialUserMessage: String? = null,
    ) {
        if (isOpen) close() // reset any previous ephemeral session

        isOpen = true
        val initial = buildList {
            if (!system.isNullOrBlank()) add(AiChatMessageDto(role = AiRole.System, content = system))
            if (!initialUserMessage.isNullOrBlank()) add(AiChatMessageDto(role = AiRole.User, content = initialUserMessage))
        }
        messages = initial
        initialMessageCount = initial.size
        isLoading = false
        input = ""
        isFullscreen = false

        scope.launch { connectAndStart(system) }
    }

    fun close() {
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        sessionId = null
        isOpen = false
        isLoading = false
        stopSpeech()
        messages = emptyList()
        input = ""
    }

    fun setInput(value: String) { input = value }

    fun toggleFullscreen() { isFullscreen = !isFullscreen }

    fun requestStopVoice() {
        stopVoiceRequest = true
        scope.launch {
            delay(100)
            stopVoiceRequest = false
        }
    }

    private var currentAudioContext: AudioContext? = null
    private var lastScheduledEndTime: Double = 0.0
    private var activeSources = mutableListOf<AudioBufferSourceNode>()

    private fun stopSpeech() {
        speechJob?.cancel()
        speechJob = null
        activeSources.forEach { it.stop() }
        activeSources.clear()
        speakingIndex = -1
        isSpeechPaused = false
        isSpeechLoading = false
        isFetchingSpeech = false
        
        currentAudioContext?.let { ctx ->
            if (ctx.state == "suspended") {
                scope.launch {
                    try { ctx.resume().await() } catch (_: Throwable) {}
                }
            }
        }
        updateMusicManager()
    }

    private fun updateMusicManager() {
        val shouldPauseMusic = speakingIndex != -1 && !isSpeechPaused
        MusicManager.setIsAiSpeaking(shouldPauseMusic)
    }

    private fun togglePauseResume() {
        val ctx = currentAudioContext ?: return
        scope.launch {
            if (ctx.state == "suspended") {
                ctx.resume().await()
                isSpeechPaused = false
            } else {
                ctx.`suspend`().await()
                isSpeechPaused = true
            }
            updateMusicManager()
        }
    }

    private fun pcm16ToFloat32(byteArray: Int8Array): Float32Array {
        val floatArray = Float32Array(byteArray.length / 2)
        for (i in 0 until floatArray.length) {
            val low = byteArray[i * 2].toInt() and 0xFF
            val high = byteArray[i * 2 + 1].toInt()
            val pcm = (high shl 8) or low
            // Convert to Float (-1.0 to 1.0)
            floatArray[i] = pcm.toShort().toFloat() / 32768f
        }
        return floatArray
    }

    fun speak(index: Int) {
        val m = messages.getOrNull(index) ?: return
        if (speakingIndex == index) {
            togglePauseResume()
            return
        }
        
        stopSpeech()

        speechJob = scope.launch {
            speakingIndex = index
            isSpeechLoading = true
            isSpeechPaused = false
            isFetchingSpeech = true
            updateMusicManager()
            
            try {
                val ctx = currentAudioContext ?: AudioContext()
                currentAudioContext = ctx
                
                if (ctx.state == "suspended") {
                    ctx.resume().await()
                }

                lastScheduledEndTime = ctx.currentTime
                activeSources.clear()

                AiApi.generateTtsStream(m.content) { chunk ->
                    while (isSpeechPaused) {
                        delay(100)
                    }
                    isSpeechLoading = false
                    val floatData = pcm16ToFloat32(chunk.unsafeCast<Int8Array>())
                    if (floatData.length == 0) return@generateTtsStream
                    
                    val buffer = ctx.createBuffer(1, floatData.length, 24000)
                    buffer.copyToChannel(floatData, 0)
                    
                    val source = ctx.createBufferSource()
                    source.buffer = buffer
                    source.connect(ctx.destination)
                    
                    val startTime = if (lastScheduledEndTime > ctx.currentTime) lastScheduledEndTime else ctx.currentTime
                    source.start(startTime)
                    lastScheduledEndTime = startTime + buffer.duration
                    activeSources.add(source)
                    
                    source.onended = {
                        activeSources.remove(source)
                        if (!isFetchingSpeech && activeSources.isEmpty() && speakingIndex == index) {
                            speakingIndex = -1
                            updateMusicManager()
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    console.error("TTS failed:", t)
                }
            } finally {
                isFetchingSpeech = false
                if (activeSources.isEmpty() && speakingIndex == index) {
                    speakingIndex = -1
                    updateMusicManager()
                }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val ws = socket ?: return

        // Optimistic append user message
        messages = messages + AiChatMessageDto(role = AiRole.User, content = trimmed)
        input = ""
        isLoading = true

        val payload = ApiClient.json.encodeToString(
            AiChatClientEvent.serializer(),
            AiChatClientEvent.UserMessage(text = trimmed),
        )
        ws.send(payload)
    }

    private fun connectAndStart(system: String?) {
        val token = UserApi.getToken() ?: ""
        val url = "${ApiConstants.API_BASE_URL.replace("http", "ws")}/ws/ai/chat?token=$token"
        val ws = WebSocket(url)
        socket = ws

        ws.onopen = {
            // Send Start with initialMessages taken from current messages
            val start = AiChatClientEvent.Start(
                system = system,
                initialMessages = messages,
            )
            val payload = ApiClient.json.encodeToString(AiChatClientEvent.serializer(), start)
            ws.send(payload)

            // If last preloaded message was user, we expect an assistant response
            if (messages.lastOrNull()?.role == AiRole.User) isLoading = true
        }

        ws.onmessage = { ev: MessageEvent ->
            val dataStr = ev.data?.toString()
            if (dataStr != null) {
                val event = runCatching {
                    ApiClient.json.decodeFromString(AiChatServerEvent.serializer(), dataStr)
                }.getOrNull()

                if (event != null) {
                    when (event) {
                        is AiChatServerEvent.SessionOpened -> {
                            sessionId = event.sessionId
                        }
                        is AiChatServerEvent.AssistantMessage -> {
                            messages = messages + AiChatMessageDto(role = AiRole.Assistant, content = event.text)
                            isLoading = false
                        }
                        is AiChatServerEvent.Error -> {
                            console.error("AI chat error: ", event.message)
                            isLoading = false
                        }
                    }
                }
            }
        }

        ws.onclose = {
            // Don’t auto-reconnect: ephemeral session ends on close.
            socket = null
        }

        ws.onerror = {
            console.error("AI chat websocket error")
            isLoading = false
        }
    }
}
