package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.w3c.dom.HTMLDivElement

/**
 * A component that uses FramedBorder with scroll images.
 *
 * @param attrs Additional attributes for the container
 * @param content The content to display inside the paper
 */
@Composable
fun Paper(
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    content: @Composable () -> Unit,
) {
    FramedBorder(
        borderImage = "/raw/data/ui/borders/scroll.png",
        darkBorderImage = "/raw/data/ui/borders/scroll_dark.png",
        backgroundImage = "/raw/data/ui/backgrounds/scroll.png",
        darkBackgroundImage = "/raw/data/ui/backgrounds/scroll_dark.png",
        attrs = attrs,
        content = content,
    )
}
