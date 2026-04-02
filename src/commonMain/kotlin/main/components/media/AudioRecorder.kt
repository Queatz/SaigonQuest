package main.components.media

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.components.audio.MusicManager
import lib.MediaRecorder
import lib.BlobPropertyBag
import lib.FilePropertyBag
import main.components.Translated
import main.theme.Styles
import main.theme.Theme
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
import org.w3c.files.Blob
import org.w3c.files.File
import web.mediadevices.getUserMedia
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import web.navigator.navigator

class AudioRecorderState(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onUpload: (File) -> Unit,
    private val onError: (String) -> Unit
) {
    var isRecording by mutableStateOf(false)
    private var isCancelled = false
    private var mediaRecorder: MediaRecorder? = null
    private val chunks = mutableListOf<Blob>()

    fun start() {
        MusicManager.startRecording()
        scope.launch {
            try {
                val constraints = MediaStreamConstraints(
                    audio = MediaTrackConstraints()
                )
                val stream = navigator.mediaDevices.getUserMedia(constraints)
                val recorder = MediaRecorder(stream)

                recorder.ondataavailable = { event ->
                    chunks.add(event.data)
                }

                recorder.onstop = {
                    if (!isCancelled) {
                        val blobProps = jso<BlobPropertyBag> {
                            type = "audio/webm"
                        }
                        val blob = Blob(chunks.toTypedArray(), blobProps.unsafeCast<org.w3c.files.BlobPropertyBag>())
                        
                        val fileProps = jso<FilePropertyBag> {
                            type = "audio/webm"
                        }
                        val file = File(arrayOf(blob), "recorded-audio.webm", fileProps.unsafeCast<org.w3c.files.FilePropertyBag>())
                        onUpload(file)
                    }
                    
                    val tracks = stream.asDynamic().getTracks() as Array<dynamic>
                    tracks.forEach { it.stop() }
                    mediaRecorder = null
                    isRecording = false
                    MusicManager.stopRecording()
                }

                recorder.onerror = {
                    onError("Recording error")
                    isRecording = false
                    MusicManager.stopRecording()
                }

                chunks.clear()
                isCancelled = false
                recorder.start()
                mediaRecorder = recorder
                isRecording = true
            } catch (t: Throwable) {
                onError(t.message ?: "Failed to start recording")
                MusicManager.stopRecording()
                isRecording = false
            }
        }
    }

    fun stop() {
        if (!stateRecordingCheck()) return
        isCancelled = false
        mediaRecorder?.stop()
    }

    fun cancel() {
        if (!stateRecordingCheck()) return
        isCancelled = true
        mediaRecorder?.stop()
    }
    
    private fun stateRecordingCheck(): Boolean = isRecording
}

@Composable
fun rememberAudioRecorderState(
    onUpload: (File) -> Unit,
    onError: (String) -> Unit = {}
): AudioRecorderState {
    val scope = rememberCoroutineScope()
    val state = remember(onUpload, onError) { AudioRecorderState(scope, onUpload, onError) }
    
    DisposableEffect(state) {
        onDispose {
            if (state.isRecording) {
                state.cancel()
            }
        }
    }
    
    return state
}

@Composable
fun AudioRecorderUI(
    state: AudioRecorderState,
    finishButtonText: String = "Send",
    isFullWidth: Boolean = false
) {
    if (!state.isRecording) return

    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            alignItems(AlignItems.Center)
            gap(8.px)
            if (isFullWidth) {
                width(100.percent)
                flex(1)
            }
        }
        onClick { it.stopPropagation() }
    }) {
        Div(attrs = {
            classes(Styles.chatButton)
            onClick {
                it.stopPropagation()
                state.cancel()
            }
            title(str("Discard"))
        }) {
            Text("🗑️")
        }

        RecordingPreview(isRecording = state.isRecording)

        Button(
            attrs = {
                style {
                    padding(8.px, 16.px)
                    backgroundColor(Theme.colors.primary)
                    color(Theme.colors.onPrimary)
                    property("border", "0")
                    borderRadius(8.px)
                    cursor("pointer")
                }
                onClick {
                    it.stopPropagation()
                    state.stop()
                }
            }
        ) {
            Text(str(finishButtonText))
        }
    }
}

@Composable
fun AudioRecorder(
    onUpload: (File) -> Unit,
    onError: (String) -> Unit = {},
    buttonAttrs: (AttrsScope<HTMLDivElement>.() -> Unit)? = null,
    buttonContent: @Composable () -> Unit = {
        Text("🎤 ")
        if (buttonAttrs == null) {
            Translated("Record")
        }
    },
    finishButtonText: String = "Send",
    isEnabled: Boolean = true,
    isFullWidth: Boolean = false,
    state: AudioRecorderState = rememberAudioRecorderState(onUpload, onError)
) {
    if (!state.isRecording) {
        if (buttonAttrs != null) {
            Div(attrs = {
                buttonAttrs()
                onClick { ev ->
                    ev.stopPropagation()
                    if (isEnabled) state.start()
                }
            }) {
                buttonContent()
            }
        } else {
            Button(attrs = {
                classes(Styles.baseDialogButtonSecondary, Styles.mediaRecorderButton)
                if (!isEnabled) disabled()
                onClick { ev ->
                    ev.stopPropagation()
                    if (isEnabled) state.start()
                }
            }) {
                buttonContent()
            }
        }
    } else {
        AudioRecorderUI(state, finishButtonText, isFullWidth)
    }
}

private inline fun <T : Any> jso(block: T.() -> Unit): T {
    val res = js("{}").unsafeCast<T>()
    block(res)
    return res
}
