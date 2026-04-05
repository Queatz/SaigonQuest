package main.components.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import lib.AudioBufferSourceNode
import lib.AudioContext
import main.api.AiApi
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

    var isSpeaking by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun stop() {
        speechJob?.cancel()
        speechJob = null
        activeSources.forEach {
            try { it.stop() } catch (_: Throwable) {}
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

    fun speak(text: String, onEnd: () -> Unit = {}) {
        stop()

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

                AiApi.generateTtsStream(text) { chunk ->
                    while (isPaused) {
                        delay(100)
                    }
                    isLoading = false
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
                        if (!isFetchingSpeech && activeSources.isEmpty()) {
                            isSpeaking = false
                            MusicManager.setIsAiSpeaking(false)
                            onEnd()
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    console.error("TTS failed:", t)
                }
            } finally {
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
