package main.components.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import lib.AudioBufferSourceNode
import lib.AudioContext
import main.api.AiApi
import org.khronos.webgl.DataView
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

object TtsManager {
    private val scope = MainScope()
    private var speechJob: Job? = null
    private var currentAudioContext: AudioContext? = null
    private var lastScheduledEndTime: Double = 0.0
    private var activeSources = mutableListOf<AudioBufferSourceNode>()
    private var isFetchingSpeech = false
    private var currentSessionId = 0

    var isSpeaking by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun stop() {
        currentSessionId++
        speechJob?.cancel()
        speechJob = null
        activeSources.forEach {
            try {
                it.onended = null
                it.stop()
            } catch (_: Throwable) {}
        }
        activeSources.clear()
        isSpeaking = false
        isPaused = false
        isLoading = false
        isFetchingSpeech = false
        
        currentAudioContext?.let { ctx ->
            if (ctx.state == "suspended") {
                scope.launch {
                    try { ctx.resume().await() } catch (_: Throwable) {}
                }
            }
        }
        MusicManager.setIsAiSpeaking(false)
    }

    fun togglePauseResume() {
        val ctx = currentAudioContext ?: return
        scope.launch {
            if (ctx.state == "suspended") {
                ctx.resume().await()
                isPaused = false
            } else {
                ctx.`suspend`().await()
                isPaused = true
            }
            MusicManager.setIsAiSpeaking(isSpeaking && !isPaused)
        }
    }

    private fun pcm16ToFloat32(byteArray: Int8Array, offset: Int = 0): Float32Array {
        val bytesToProcess = byteArray.length - offset
        val samplesCount = bytesToProcess / 2
        val floatArray = Float32Array(samplesCount)
        val dataView = DataView(byteArray.buffer, byteArray.byteOffset + offset, bytesToProcess)
        
        for (i in 0 until samplesCount) {
            // Read as signed 16-bit little-endian
            val pcm = dataView.getInt16(i * 2, true)
            floatArray[i] = pcm.toFloat() / 32768f
        }
        return floatArray
    }

    fun speak(text: String, onEnd: () -> Unit = {}) {
        SoundManager.stopAllSounds()
        if (text.isBlank()) {
            onEnd()
            return
        }

        val sessionId = currentSessionId
        speechJob = scope.launch {
            isSpeaking = true
            isLoading = true
            isPaused = false
            isFetchingSpeech = true
            MusicManager.setIsAiSpeaking(true)
            
            try {
                val ctx = currentAudioContext ?: AudioContext()
                currentAudioContext = ctx
                
                if (ctx.state == "suspended") {
                    ctx.resume().await()
                }

                lastScheduledEndTime = ctx.currentTime
                activeSources.clear()

                var firstChunkChecked = false
                var bytesToSkip = 0
                AiApi.generateTtsStream(text) { chunk ->
                    if (sessionId != currentSessionId) return@generateTtsStream
                    
                    while (isPaused) {
                        delay(100)
                        if (sessionId != currentSessionId) return@generateTtsStream
                    }
                    isLoading = false
                    val bytes = chunk.unsafeCast<Int8Array>()
                    var chunkOffset = 0
                    
                    if (!firstChunkChecked) {
                        firstChunkChecked = true
                        val sigStr = (0 until 4.coerceAtMost(bytes.length)).map { bytes[it].toInt() and 0xFF }.joinToString("") { it.toChar().toString() }
                        val hex = (0 until 4.coerceAtMost(bytes.length)).map { bytes[it].toInt() and 0xFF }.joinToString(" ") { it.toString(16).padStart(2, '0') }
                        console.log("TTS Stream start. Signature: $sigStr, Hex: $hex, First chunk size: ${bytes.length}")
                        
                        if (sigStr == "RIFF") {
                            bytesToSkip = 44
                            console.log("Detected WAV format, skipping 44-byte header")
                        } else if (sigStr.startsWith("ID3") || (bytes.length >= 2 && (bytes[0].toInt() and 0xFF == 0xFF) && (bytes[1].toInt() and 0xE0 == 0xE0))) {
                            console.error("CRITICAL: Detected MP3/AAC instead of PCM! Pure static will occur. Hex: $hex")
                        } else {
                            console.log("Assuming raw PCM format")
                        }
                    }

                    if (bytesToSkip > 0) {
                        val skip = bytesToSkip.coerceAtMost(bytes.length)
                        chunkOffset = skip
                        bytesToSkip -= skip
                        if (bytes.length <= chunkOffset) return@generateTtsStream
                    }
                    
                    val floatData = pcm16ToFloat32(bytes, chunkOffset)
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
                        if (sessionId == currentSessionId) {
                            activeSources.remove(source)
                            if (!isFetchingSpeech && activeSources.isEmpty()) {
                                isSpeaking = false
                                MusicManager.setIsAiSpeaking(false)
                                onEnd()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    console.error("TTS failed:", t)
                }
            } finally {
                if (sessionId == currentSessionId) {
                    isFetchingSpeech = false
                    if (activeSources.isEmpty()) {
                        isSpeaking = false
                        MusicManager.setIsAiSpeaking(false)
                        onEnd()
                    }
                }
            }
        }
    }
}
