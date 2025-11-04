package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.CSSColorValue
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flex
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.gridTemplateColumns
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.media
import org.jetbrains.compose.web.css.mediaMaxWidth
import org.jetbrains.compose.web.css.minHeight
import org.jetbrains.compose.web.css.minWidth
import org.jetbrains.compose.web.css.overflowY
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.width

object WorldDocsStyles : StyleSheet() {
    // Indentation helpers for tree levels
    val indent0 by style { marginLeft(0.px) }
    val indent1 by style { marginLeft(12.px) }
    val indent2 by style { marginLeft(24.px) }
    val indent3 by style { marginLeft(36.px) }
    val indent4 by style { marginLeft(48.px) }
    val indent5 by style { marginLeft(60.px) }

    // Main dialog content area, uses grid for left tree + right content
    val content by style {
        display(DisplayStyle.Grid)
        gridTemplateColumns("280px 1fr")
        gap(16.px)
        height(0.px)
        flexGrow(1)
    }

    // Header actions row (create + search)
    val headerBar by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(12.px)
        padding(8.px)
        borderRadius(8.px)
        backgroundColor(Theme.colors.surface)
        dark(self) { backgroundColor(Theme.colors.surfaceDark) }
    }

    // Left panel container
    val leftPanel by style {
        backgroundColor(Theme.colors.surface)
        borderRadius(8.px)
        padding(8.px)
        overflowY("auto")
        minHeight(0.px) // allow flex/grid children to shrink
        dark(self) { backgroundColor(Theme.colors.surfaceDark) }
    }

    // Right panel container
    val rightPanel by style {
        backgroundColor(Theme.colors.surface)
        borderRadius(8.px)
        padding(12.px)
        overflowY("auto")
        minHeight(0.px)
        dark(self) { backgroundColor(Theme.colors.surfaceDark) }
    }

    // Search input styling hook
    val searchInput by style {
        flex(1)
        minWidth(0.px)
        padding(8.px)
        borderRadius(6.px)
        backgroundColor(Theme.colors.surfaceVariant)
        border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtle)
        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtleDark)
            color(Theme.colors.onSurfaceDark)
        }
    }

    // Tree list
    val tree by style {
        margin(0.px)
        padding(0.px)
        property("list-style", "none")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(2.px)
    }

    // Tree item
    val treeItem by style {
        padding(8.px)
        borderRadius(6.px)
        cursor("pointer")
        color(Theme.colors.onSurface)
        width(100.percent)
        boxSizing("border-box")
        self + hover style {
            backgroundColor(Theme.colors.surfaceVariant)
        }
        dark(self) {
            color(Theme.colors.onSurfaceDark)
            self + hover style {
                backgroundColor(Theme.colors.surfaceVariantDark)
            }
        }
    }

    // Selected tree item
    val treeItemSelected by style {
        padding(8.px)
        borderRadius(6.px)
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
    }

    // Right panel: top controls row (rename/delete)
    val controlsRow by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(8.px)
        marginBottom(8.px)
    }

    val titleInput by style {
        padding(8.px)
        borderRadius(6.px)
        backgroundColor(Theme.colors.surfaceVariant)
        border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtle)
        minWidth(200.px)
        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtleDark)
            color(Theme.colors.onSurfaceDark)
        }
    }

    val warningText by style {
        color(Theme.colors.accentWarnStrong)
        fontSize(12.px)
        marginLeft(8.px)
    }

    val statusText by style {
        fontSize(12.px)
        color(Theme.colors.onSurfaceVariant)
        marginTop(6.px)
    }

    // Connection status chip (header)
    val statusChip by style {
        fontSize(12.px)
        padding(4.px, 10.px)
        borderRadius(12.px)
    }
    val statusChipOnline by style {
        backgroundColor(Theme.colors.successSoftBg)
        color(Theme.colors.success)
        border(1.px, LineStyle.Solid, Theme.colors.successSoftBorder)
    }
    val statusChipReconnecting by style {
        backgroundColor(Theme.colors.accentWarnSoft)
        color(Theme.colors.accentWarnStrong)
        border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtle)
    }

    // Drag-and-drop drop zones
    val dropZone by style {
        height(6.px)
        backgroundColor(Color.transparent)
        borderRadius(4.px)
    }
    val dropZoneActive by style {
        height(6.px)
        backgroundColor(Theme.colors.accentInfoSoft)
        borderRadius(4.px)
    }
    val dropInto by style {
        border(1.px, LineStyle.Dashed, Theme.colors.panelBorderSubtle)
        borderRadius(4.px)
        margin(2.px)
    }

    // Presence styles
    val presenceRow by style {
        marginTop(8.px)
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(8.px)
    }
    val presenceUser by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(6.px)
        color(Theme.colors.onSurface)
        dark(self) { color(Theme.colors.onSurfaceDark) }
        fontSize(12.px)
    }
    private fun presenceDotColor(c: CSSColorValue) = style {
        width(8.px)
        height(8.px)
        borderRadius(8.px)
        backgroundColor(c)
    }
    val presenceDot0 by presenceDotColor(Theme.colors.accentInfo)
    val presenceDot1 by presenceDotColor(Theme.colors.accentWarn)
    val presenceDot2 by presenceDotColor(Theme.colors.accentSuccess)
    val presenceDot3 by presenceDotColor(Theme.colors.secondary)
    val presenceDot4 by presenceDotColor(Theme.colors.primary)
    val presenceDot5 by presenceDotColor(Theme.colors.textSecondary)

    // Small screens: stack panels vertically
    val contentMobile by style {
        media(mediaMaxWidth(768.px)) {
            self style {
                gridTemplateColumns("1fr")
            }
        }
    }
}
