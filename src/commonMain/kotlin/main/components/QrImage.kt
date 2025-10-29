package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.Img

/**
 * Reusable QR image that applies pixelated rendering and sizing.
 */
@Composable
fun QrImage(
    src: String,
) {
    Img(
        src = src,
        attrs = {
            classes(Styles.qrImage)
        }
    )
}
