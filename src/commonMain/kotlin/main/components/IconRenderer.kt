package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.css.backgroundImage
import org.jetbrains.compose.web.css.backgroundPosition
import org.jetbrains.compose.web.css.backgroundSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div

/**
 * Renders an icon from a sprite sheet based on its index.
 * 
 * @param url The URL of the sprite sheet
 * @param index The index of the icon in the sprite sheet
 * @param size The size of the rendered icon in pixels (default: 32px)
 * @param onClick Optional click handler
 */
@Composable
fun IconRenderer(
    url: String,
    index: Int,
    size: Int = 32,
    onClick: (() -> Unit)? = null
) {
    Div(
        attrs = {
            classes(Styles.iconRenderer)
            
            style {
                // Override default size if needed
                if (size != 32) {
                    width(size.px)
                    height(size.px)
                }

                backgroundImage("url(\"$url\")")
                backgroundSize("320px 320px")

                // Calculate position based on index
                // index moves horizontally and wraps vertically
                val col = index % 10 // 10 columns (320px / 32px = 10)
                val row = index / 10
                backgroundPosition("-${col * 32}px -${(10 - row) * 32}px")
            }
            
            // Add click handler if provided
            onClick?.let { handler ->
                onClick { handler() }
            }
        }
    ) {}
}
