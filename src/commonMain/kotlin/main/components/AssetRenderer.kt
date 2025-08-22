package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import models.AssetSearchResult
import models.AssetType
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.backgroundImage
import org.jetbrains.compose.web.css.backgroundPosition
import org.jetbrains.compose.web.css.backgroundRepeat
import org.jetbrains.compose.web.css.backgroundSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.lineHeight
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun AssetRenderer(
    result: AssetSearchResult,
    size: Int = 96,
) {
    result.type ?: return
    result.asset ?: return

    when (result.type!!) {
        AssetType.Items -> {
            val index = result.index ?: 0
            IconRenderer(
                url = "/raw/data/spritesheets/${result.asset!!.file}.png",
                index = index,
                size = size.coerceAtMost(64),
            )
        }
        AssetType.SpriteSheet -> {
            // Render first 32x48 cell
            val w = 32
            val h = 48
            Div(attrs = {
                classes(Styles.iconRenderer)
                style {
                    width(w.px)
                    height(h.px)
                    backgroundImage("url(\"/raw/data/spritesheets/${result.asset!!.file}.png\")")
                    backgroundRepeat("no-repeat")
                    backgroundSize("${w * 8}px ${h * 8}px")
                    // First cell at (0,0)
                    backgroundPosition("0px 0px")
                }
            }) {}
        }
        AssetType.World -> {
            BasicSceneRenderer(
                filename = result.asset!!.file,
            )
        }
        AssetType.Objects -> {
            BasicSceneRenderer(
                filename = result.asset!!.file,
                collection = result.collection,
            )
        }
    }
    // Simple caption
    P(attrs = {
        style {
            textAlign("center")
            lineHeight(20.px)
            marginBottom(4.px)
            color(Color.white)
        }
    }) { Text(result.title ?: result.asset!!.file) }
}
