package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.ApiClient
import main.api.ApiConstants
import main.api.UserApi
import main.theme.Styles
import org.jetbrains.compose.web.dom.Button
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
    onText: (String) -> Unit,
) {
    val scope = remember { MainScope() }
    var isRecording by remember { mutableStateOf(false) }
    var socket: WebSocket? by remember { mutableStateOf(null) }
    var mediaStream: MediaStream? by remember { mutableStateOf(null) }
    var audioContext: lib.AudioContext? by remember { mutableStateOf(null) }
    var sourceNode: lib.MediaStreamAudioSourceNode? by remember { mutableStateOf(null) }
    var processorNode: lib.ScriptProcessorNode? by remember { mutableStateOf(null) }
    var flushPending: (() -> Unit)? by remember { mutableStateOf(null) }

    fun stopRecording() {
        // Flush any buffered audio before signaling stop
        runCatching { flushPending?.invoke() }
        try {
            val s = socket
            if (s != null) {
                val msg = ApiClient.json.encodeToString(VoiceControlMessage.serializer(), VoiceControlMessage(type = "stop"))
                s.send(msg)
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
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) stopRecording()
        }
    }

    Button(
        attrs = {
            classes(Styles.voiceButton)
            onClick {
                if (isRecording) {
                    stopRecording()
                } else {
                    scope.launch {
                        runCatching {
                            // Request microphone
                            val constraints = MediaStreamConstraints(
                                audio = MediaTrackConstraints()
                            )
                            val stream = navigator.mediaDevices.getUserMedia(constraints)
                            mediaStream = stream

                            // Connect WebSocket to backend proxy
                            val token = UserApi.getToken() ?: ""
                            val wsUrl = "${ApiConstants.API_BASE_URL}/ws/voice?token=$token"
                            val ws = WebSocket(wsUrl)
                            ws.onopen = { _: org.w3c.dom.events.Event ->
                                val msg = ApiClient.json.encodeToString(
                                    VoiceControlMessage.serializer(),
                                    VoiceControlMessage(type = "start"),
                                )
                                ws.send(msg)
                            }
                            ws.onmessage = { event: MessageEvent ->
                                val dataAny: Any? = event.data as Any?
                                val dataStr: String? = when (dataAny) {
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
                            }
                            ws.onerror = { _: org.w3c.dom.events.Event ->
                                console.error("VoiceInput: websocket error")
                            }
                            socket = ws

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
                                if (!force && bytesAccum < 4800) return
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
            }
        }
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
