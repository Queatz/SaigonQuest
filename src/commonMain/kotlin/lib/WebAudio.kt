package lib

import org.khronos.webgl.Float32Array
import web.mediastreams.MediaStream

// Minimal Web Audio externals for broad browser compatibility (including Firefox)
// ScriptProcessorNode is deprecated but widely supported; suitable as a fallback.

external open class AudioNode {
    fun connect(destination: AudioNode)
    fun disconnect()
}

external class AudioContext {
    val sampleRate: Float
    val destination: AudioNode
    fun createMediaStreamSource(stream: MediaStream): MediaStreamAudioSourceNode
    fun createScriptProcessor(
        bufferSize: Int = definedExternally,
        numberOfInputChannels: Int = definedExternally,
        numberOfOutputChannels: Int = definedExternally,
    ): ScriptProcessorNode
    fun close()
}

external class MediaStreamAudioSourceNode : AudioNode

external class ScriptProcessorNode : AudioNode {
    var onaudioprocess: ((AudioProcessingEvent) -> Unit)?
    val bufferSize: Int
}

external class AudioProcessingEvent {
    val inputBuffer: AudioBuffer
    val outputBuffer: AudioBuffer
}

external class AudioBuffer {
    val sampleRate: Int
    val length: Int
    fun getChannelData(channel: Int): Float32Array
}
