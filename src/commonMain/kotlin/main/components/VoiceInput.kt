package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import main.api.ApiClient
import main.api.ApiConstants
import main.api.UserApi
import main.components.audio.MusicManager
import main.theme.Styles
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.WebSocket
import web.mediadevices.getUserMedia
import web.mediastreams.MediaStream
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import web.navigator.navigator
import kotlinx.serialization.Serializable
import org.w3c.dom.MessageEvent
import org.khronos.webgl.Int16Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import lib.AudioContext

@Serializable
private data class VoiceControlMessage(
    val type: String,
)

@Serializable
private data class VoiceTranscriptMessage(
    val type: String, // "partial" | "final"
    val text: String,
)

@Composable
fun VoiceInput(
    small: Boolean = false,
    enabled: Boolean = true,
    stopRequested: Boolean = false,
    onText: (String) -> Unit,
) {
    val scope = remember { MainScope() }
    var isRecording by remember { mutableStateOf(false) }
    var socket: WebSocket? by remember { mutableStateOf(null) }
    var mediaStream: MediaStream? by remember { mutableStateOf(null) }
    var audioContext: AudioContext? by remember { mutableStateOf(null) }
    var sourceNode: lib.MediaStreamAudioSourceNode? by remember { mutableStateOf(null) }
    var processorNode: lib.ScriptProcessorNode? by remember { mutableStateOf(null) }
    var flushPending: (() -> Unit)? by remember { mutableStateOf(null) }
    // Reconnect control
    var allowReconnect by remember { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableStateOf(0) }
    var reconnectJob: Job? by remember { mutableStateOf(null) }

    fun scheduleReconnect(connectSocket: () -> Unit) {
        // Only if user is still recording and reconnection allowed
        if (!isRecording || !allowReconnect) return
        // Avoid multiple concurrent schedules
        if (reconnectJob?.isActive == true) return
        val attempts = reconnectAttempts + 1
        reconnectAttempts = attempts
        // Exponential backoff with jitter: base 300ms up to 10s
        val baseMs = 300
        val maxMs = 10_000
        val delayMs = kotlin.math.min(maxMs, baseMs * (1 shl kotlin.math.min(8, attempts)))
        val jitter = (delayMs * 0.2).toInt() // +/-20%
        val actualDelay = (delayMs - jitter) + kotlin.random.Random.nextInt(jitter * 2 + 1)
        reconnectJob = scope.launch {
            try {
                kotlinx.coroutines.delay(actualDelay.toLong())
                // If user stopped in the meantime, abort
                if (!isRecording || !allowReconnect) return@launch
                // Attempt a new connection
                connectSocket()
            } catch (_: Throwable) {
                // If scheduling fails, we’ll try again on the next onclose/error
            }
        }
    }

    fun connectSocket(wsUrl: String) {
        // Capture URL for future reconnects
        val ws = WebSocket(wsUrl)
        ws.onopen = { _: org.w3c.dom.events.Event ->
            // Reset backoff
            reconnectAttempts = 0
            // When (re)connected during an active recording session, inform backend
            try {
                val msg = ApiClient.json.encodeToString(
                    VoiceControlMessage.serializer(),
                    VoiceControlMessage(type = "start"),
                )
                ws.send(msg)
            } catch (_: Throwable) {}
        }
        ws.onmessage = { event: MessageEvent ->
            val dataStr: String? = when (val dataAny: Any? = event.data) {
                is String -> dataAny
                else -> dataAny?.toString()
            }
            if (dataStr != null) {
                val msg = runCatching { ApiClient.json.decodeFromString(VoiceTranscriptMessage.serializer(), dataStr) }.getOrNull()
                if (msg != null && msg.type == "final" && msg.text.isNotBlank()) {
                    onText(msg.text)
                }
            }
        }
        ws.onclose = { _: org.w3c.dom.events.Event ->
            socket = null
            // Auto-reconnect if still recording
            scheduleReconnect { connectSocket(wsUrl) }
        }
        ws.onerror = { _: org.w3c.dom.events.Event ->
            console.error("VoiceInput: websocket error")
            // In some browsers onerror precedes onclose; schedule reconnect just in case
            scheduleReconnect { connectSocket(wsUrl) }
        }
        socket = ws
    }

    fun stopRecording() {
        if (!isRecording) return
        // Prevent future reconnects and cancel any scheduled one
        allowReconnect = false
        try { reconnectJob?.cancel() } catch (_: Throwable) {}
        reconnectJob = null
        // Flush any buffered audio before signaling stop
        runCatching { flushPending?.invoke() }
        try {
            val s = socket
            if (s != null) {
                val msg = ApiClient.json.encodeToString(VoiceControlMessage.serializer(), VoiceControlMessage(type = "stop"))
                s.send(msg)
                // Close the socket gracefully to free server resources
                runCatching { s.close() }
            }
        } catch (_: Throwable) {}
        // Tear down audio graph
        try { processorNode?.onaudioprocess = null } catch (_: Throwable) {}
        try { sourceNode?.disconnect() } catch (_: Throwable) {}
        try { processorNode?.disconnect() } catch (_: Throwable) {}
        try { audioContext?.close() } catch (_: Throwable) {}
        try { mediaStream?.getTracks()?.forEach { it.stop() } } catch (_: Throwable) {}
        audioContext = null
        sourceNode = null
        processorNode = null
        mediaStream = null
        flushPending = null
        isRecording = false
        MusicManager.stopRecording()
    }

    LaunchedEffect(stopRequested) {
        if (stopRequested && isRecording) {
            stopRecording()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) stopRecording()
        }
    }

    DialogButton(
        onClick = {
            if (isRecording) {
                stopRecording()
            } else {
                MusicManager.startRecording()
                scope.launch {
                    runCatching {
                        // Request microphone
                        val constraints = MediaStreamConstraints(
                            audio = MediaTrackConstraints()
                        )
                        val stream = navigator.mediaDevices.getUserMedia(constraints)
                        mediaStream = stream

                        // Connect WebSocket to backend proxy
                        val user = UserApi.getCurrentUser()
                        val wsUrl = "${ApiConstants.API_BASE_URL}/ws/voice?language=${user.language}"
                        allowReconnect = true
                        reconnectAttempts = 0
                        connectSocket(wsUrl)

                        // Web Audio fallback (Firefox compatible): AudioContext + ScriptProcessorNode
                        val ctx = AudioContext()
                        audioContext = ctx
                        val src = ctx.createMediaStreamSource(stream)
                        sourceNode = src
                        val proc = ctx.createScriptProcessor(
                            2048,
                            1,
                            1,
                        )
                        processorNode = proc

                        // Accumulate PCM16 chunks to ~100 ms before sending
                        val chunks = mutableListOf<Int16Array>()
                        var bytesAccum = 0
                        fun flushIfReady(force: Boolean = false) {
                            if (bytesAccum == 0) return
                            if (!force && bytesAccum < 640) return
                            val totalSamples = bytesAccum / 2
                            val merged = Int16Array(totalSamples)
                            var offset = 0
                            for (c in chunks) {
                                merged.set(c, offset)
                                offset += c.length
                            }
                            chunks.clear()
                            bytesAccum = 0
                            try {
                                socket?.send(merged.buffer)
                            } catch (t: Throwable) {
                                console.error("VoiceInput: failed to send binary audio", t)
                            }
                        }
                        // Expose a flush callback so we can force-send pending audio before stopping
                        flushPending = { flushIfReady(true) }

                        val inRate = ctx.sampleRate.toInt()
                        proc.onaudioprocess = { ev ->
                            try {
                                val input = ev.inputBuffer
                                val ch0 = input.getChannelData(0)
                                val resampled = resampleTo24k(ch0, inRate)
                                val pcm16 = floatToPcm16(resampled)
                                chunks.add(pcm16)
                                bytesAccum += pcm16.length * 2
                                flushIfReady(false)
                            } catch (t: Throwable) {
                                console.error("VoiceInput: onaudioprocess error", t)
                            }
                        }

                        // Build graph; connect to destination to keep processor active
                        src.connect(proc)
                        proc.connect(ctx.destination)

                        isRecording = true
                    }.onFailure {
                        console.error("VoiceInput: failed to start recording", it)
                        stopRecording()
                    }
                }
            }
        },
        style = if (isRecording) DialogButtonStyle.Danger else DialogButtonStyle.Good,
        isDisabled = !enabled,
        small = small,
        attrs = {
            if (!isRecording) {
                classes(Styles.voiceInputPulseClass)
            }
        },
    ) {
        if (isRecording) Text("🛑") else Text("🎙️")
    }
}


// --- Audio helpers: resampling and PCM conversion ---
private fun resampleTo24k(input: org.khronos.webgl.Float32Array, inputSampleRate: Int): org.khronos.webgl.Float32Array {
    // Fast path: already 24 kHz
    if (inputSampleRate == 24_000) return input

    val inLen = input.length
    if (inLen == 0) return org.khronos.webgl.Float32Array(0)

    // Common path: 48 kHz → 24 kHz (decimate-by-2 with simple 2-tap low-pass average)
    if (inputSampleRate == 48_000) {
        val outLen = inLen / 2
        val out = org.khronos.webgl.Float32Array(outLen)
        var i = 0
        var o = 0
        while (o < outLen && (i + 1) < inLen) {
            val v = (input[i] + input[i + 1]) * 0.5f
            out[o] = v
            i += 2
            o += 1
        }
        // If inLen is odd, last sample is dropped, which is fine for streaming
        return out
    }

    // Generic path: linear resample from inputSampleRate → 24 kHz
    val step = inputSampleRate.toDouble() / 24_000.0
    val outLen = kotlin.math.max(1, kotlin.math.floor(inLen / step).toInt())
    val out = org.khronos.webgl.Float32Array(outLen)

    var t = 0.0 // position in input samples
    var o = 0
    while (o < outLen) {
        val idx = kotlin.math.floor(t).toInt()
        val frac = (t - idx).toFloat()
        val s0 = input[if (idx < inLen) idx else inLen - 1]
        val s1 = input[if (idx + 1 < inLen) idx + 1 else inLen - 1]
        val v = s0 + (s1 - s0) * frac
        out[o] = v
        t += step
        o += 1
    }
    return out
}

private fun floatToPcm16(input: org.khronos.webgl.Float32Array): Int16Array {
    val n = input.length
    val out = Int16Array(n)
    var i = 0
    while (i < n) {
        var x = input[i]
        if (x > 1f) x = 1f
        if (x < -1f) x = -1f
        // Map [-1.0, 1.0] to Int16 range [-32768, 32767]
        val s = if (x < 0f) (x * 32768f).toInt() else (x * 32767f).toInt()
        out[i] = s.toShort()
        i += 1
    }
    return out
}
