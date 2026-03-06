package main.components.media

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.components.audio.MusicManager
import lib.BlobEvent
import lib.MediaRecorder
import lib.BlobPropertyBag
import lib.FilePropertyBag
import main.components.Translated
import main.theme.Styles
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.files.Blob
import org.w3c.files.File
import web.mediadevices.MediaDevices
import web.mediadevices.getUserMedia
import web.mediastreams.MediaStream
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaTrackConstraints
import web.navigator.navigator

@Composable
fun AudioRecorder(
    onUpload: (File) -> Unit,
    onError: (String) -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var isCancelled by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    val chunks = remember { mutableListOf<Blob>() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                mediaRecorder?.stop()
                MusicManager.stopRecording()
            }
        }
    }

    fun startRecording() {
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
                    
                    // Stop all tracks to release the microphone
                    val tracks = stream.asDynamic().getTracks() as Array<dynamic>
                    tracks.forEach { it.stop() }
                }

                recorder.onerror = {
                    onError("Recording error")
                }

                chunks.clear()
                isCancelled = false
                recorder.start()
                mediaRecorder = recorder
                isRecording = true
            } catch (t: Throwable) {
                onError(t.message ?: "Failed to start recording")
                MusicManager.stopRecording()
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isCancelled = false
        mediaRecorder?.stop()
        isRecording = false
        MusicManager.stopRecording()
    }

    fun cancelRecording() {
        if (!isRecording) return
        isCancelled = true
        mediaRecorder?.stop()
        isRecording = false
        MusicManager.stopRecording()
    }

    if (!isRecording) {
        Button(attrs = {
            classes(Styles.baseDialogButtonSecondary, Styles.mediaRecorderButton)
            onClick { ev ->
                ev.stopPropagation()
                startRecording()
            }
        }) {
            Text("🎤")
            Translated("Record audio")
        }
    } else {
        Button(attrs = {
            classes(Styles.baseDialogButtonSecondary, Styles.mediaRecorderButton, Styles.mediaRecorderButtonRecording)
            onClick { ev ->
                ev.stopPropagation()
                stopRecording()
            }
        }) {
            Text("🛑")
            Translated("Stop recording")
        }

        Button(attrs = {
            classes(Styles.baseDialogButtonSecondary, Styles.mediaRecorderButton)
            onClick { ev ->
                ev.stopPropagation()
                cancelRecording()
            }
        }) {
            Text("🗑️")
            Translated("Discard recording")
        }
    }
}

// Helper for creating JS objects from interfaces
private inline fun <T : Any> jso(block: T.() -> Unit): T {
    val res = js("{}").unsafeCast<T>()
    block(res)
    return res
}
