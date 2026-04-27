package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLDivElement

/**
 * A component that frames its contents with a 3x3 image using pixelated rendering.
 *
 * @param borderImage The URL of the image to use for the border
 * @param darkBorderImage The URL of the image to use for the border in dark mode
 * @param backgroundImage The URL of the image to use for the background
 * @param darkBackgroundImage The URL of the image to use for the background in dark mode
 * @param attrs Additional attributes for the container
 * @param content The content to display inside the frame
 */
@Composable
fun FramedBorder(
    borderImage: String? = null,
    darkBorderImage: String? = null,
    backgroundImage: String? = null,
    darkBackgroundImage: String? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    content: @Composable () -> Unit
) {
    val isDark = rememberDarkMode()

    Div(
        attrs = {
            classes(Styles.framedBorder)

            val currentBg = if (isDark && darkBackgroundImage != null) darkBackgroundImage else backgroundImage
            val currentBorder = if (isDark && darkBorderImage != null) darkBorderImage else borderImage

            if (currentBorder != null || currentBg != null) {
                style {
                    if (currentBorder != null) {
                        property("border-image-source", "url(\"$currentBorder\")")
                    }
                    if (currentBg != null) {
                        property("background-image", "url(\"$currentBg\")")
                    }
                }
            }

            attrs?.invoke(this)
        }
    ) {
        content()
    }
}
