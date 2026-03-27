package lib

import org.khronos.webgl.Float32Array
import web.mediastreams.MediaStream
import web.events.Event

// Minimal Web Audio externals for broad browser compatibility (including Firefox)
// ScriptProcessorNode is deprecated but widely supported; suitable as a fallback.

external open class AudioNode {
    fun connect(destination: AudioNode)
    fun disconnect()
}

external class AudioContext() {
    val sampleRate: Float
    val destination: AudioNode
    val currentTime: Double
    val state: String
    fun resume(): kotlin.js.Promise<Unit>
    fun createMediaStreamSource(stream: MediaStream): MediaStreamAudioSourceNode
    fun createScriptProcessor(
        bufferSize: Int = definedExternally,
        numberOfInputChannels: Int = definedExternally,
        numberOfOutputChannels: Int = definedExternally,
    ): ScriptProcessorNode
    fun createBuffer(numberOfChannels: Int, length: Int, sampleRate: Int): AudioBuffer
    fun createBufferSource(): AudioBufferSourceNode
    fun close()
}

external class AudioBufferSourceNode : AudioNode {
    var buffer: AudioBuffer?
    var onended: ((Event) -> Unit)?
    fun start(when_: Double = definedExternally, offset: Double = definedExternally, duration: Double = definedExternally)
    fun stop(when_: Double = definedExternally)
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
    val duration: Double
    val numberOfChannels: Int
    fun getChannelData(channel: Int): Float32Array
    fun copyToChannel(source: Float32Array, channelNumber: Int, startInChannel: Int = definedExternally)
}
