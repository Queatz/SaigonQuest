package main.components.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Controller for requesting the chat rich text editor to open with prefilled content.
 *
 * Usage:
 * - Call [openRichEditor] with the target worldId and initial text.
 * - [ChatContent] listens to [requests] and opens the editor when the worldId matches.
 */
object ChatComposerController {
    data class Request(
        val worldId: String,
        val initialText: String,
    )

    private val _requests = MutableStateFlow<Request?>(null)
    val requests: StateFlow<Request?> = _requests

    fun openRichEditor(
        worldId: String,
        initialText: String,
    ) {
        _requests.value = Request(worldId = worldId, initialText = initialText)
    }

    fun clear() {
        _requests.value = null
    }
}
