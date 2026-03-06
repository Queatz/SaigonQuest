package main.components

import androidx.compose.runtime.Composable
import main.theme.AssetStyles
import main.theme.Styles
import models.AssetSearchResult
import models.AssetType
import org.jetbrains.compose.web.css.backgroundImage
import org.jetbrains.compose.web.dom.Div
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
            // Render first 32x48 cell (bottom left)
            Div(attrs = {
                classes(Styles.iconRenderer)
                classes(AssetStyles.assetSpriteSheetIcon)
                style {
                    backgroundImage("url(\"/raw/data/spritesheets/${result.asset!!.file}.png\")")
                }
            }) {}
        }
        AssetType.World -> {
            BasicSceneRenderer(
                filename = result.asset!!.file,
                transparentBackground = true,
            )
        }
        AssetType.Objects -> {
            BasicSceneRenderer(
                filename = result.asset!!.file,
                collection = result.collection,
                transparentBackground = true,
            )
        }
    }
    // Simple caption
    Div(attrs = {
        classes(AssetStyles.assetCaption)
    }) {
        Text(result.title ?: result.asset!!.file)
    }
}
