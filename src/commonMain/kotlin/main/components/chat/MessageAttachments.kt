package main.components.chat

import androidx.compose.runtime.Composable
import main.theme.Styles
import models.chat.Attachment
import models.chat.ImageAttachment
import org.jetbrains.compose.web.dom.Img

/**
 * Reusable renderer for message attachments.
 * - Currently supports ImageAttachment.
 * - Unknown attachment types are ignored (no-op).
 */
@Composable
fun MessageAttachments(
    attachments: List<Attachment>,
    onImageClick: (String) -> Unit = {},
    onImageLoaded: () -> Unit = {}
) {
    attachments.forEach { att ->
        when (att) {
            is ImageAttachment -> {
                Img(
                    src = att.url,
                    alt = "image",
                    attrs = {
                        classes(Styles.chatImageAttachment)
                        onClick {
                            it.stopPropagation()
                            onImageClick(att.url)
                        }
                        ref { img ->
                            var remove: (() -> Unit)? = null
                            if (img.complete && img.naturalHeight > 0) {
                                onImageLoaded()
                            } else {
                                val listener: (org.w3c.dom.events.Event) -> Unit = { onImageLoaded() }
                                img.addEventListener("load", listener)
                                remove = { img.removeEventListener("load", listener) }
                            }
                            onDispose {
                                remove?.invoke()
                            }
                        }
                    }
                )
            }
            else -> { /* no-op for now */ }
        }
    }
}
