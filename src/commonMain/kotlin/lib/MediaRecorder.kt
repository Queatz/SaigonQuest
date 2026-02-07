package lib

import web.events.Event
import web.events.EventTarget
import web.mediastreams.MediaStream
import org.w3c.files.Blob

external class MediaRecorder(stream: MediaStream) : EventTarget {
    var ondataavailable: ((BlobEvent) -> Unit)?
    var onstop: ((Event) -> Unit)?
    var onerror: ((Event) -> Unit)?

    val state: String

    fun start()
    fun stop()
}

external class BlobEvent : Event {
    val data: Blob
}

external interface BlobPropertyBag {
    var type: String?
}

external interface FilePropertyBag : BlobPropertyBag {
    var lastModified: Double?
}
