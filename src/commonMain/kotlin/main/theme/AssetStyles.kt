package main.theme

import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.backgroundPosition
import org.jetbrains.compose.web.css.backgroundRepeat
import org.jetbrains.compose.web.css.backgroundSize
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.gridTemplateColumns
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.lineHeight
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.FlexWrap
import org.jetbrains.compose.web.css.flexWrap
import org.jetbrains.compose.web.css.marginTop

/**
 * Styles dedicated to asset/admin related UI pieces such as the asset browser and renderer.
 */
object AssetStyles : StyleSheet() {
    // Container for the asset browser dialog content
    val assetDialogContent by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(8.px)
        // Slight breathing room inside dialog content
        property("padding", "8px")
    }

    // Row for filters (title + category). Wrap on small screens
    val assetFiltersRow by style {
        display(DisplayStyle.Flex)
        gap(8.px)
        flexWrap(FlexWrap.Wrap)
    }

    // Grid containing search results
    val assetGrid by style {
        display(DisplayStyle.Grid)
        gridTemplateColumns("repeat(auto-fill, minmax(128px, 1fr))")
        gap(12.px)
        marginBottom(8.px)
    }

    // SpriteSheet preview cell style (first 32x48 cell of an 8x8 sheet)
    val assetSpriteSheetIcon by style {
        width(32.px)
        height(48.px)
        backgroundColor(Color.transparent)
        backgroundRepeat("no-repeat")
        // 32*8 x 48*8
        backgroundSize("256px 256px")
        // Index 0 starts at bottom left
        backgroundPosition("0px -208px")
    }

    // Caption under each asset preview
    val assetCaption by style {
        textAlign("center")
        lineHeight(20.px)
        marginTop(0.px)
        marginBottom(4.px)
        color(Theme.colors.onBackground)

        dark(self) { color(Theme.colors.onBackgroundDark) }
    }
}
