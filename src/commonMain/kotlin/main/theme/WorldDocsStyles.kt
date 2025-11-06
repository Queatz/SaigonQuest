package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.CSSColorValue
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.FlexWrap
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
import org.jetbrains.compose.web.css.flexWrap
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
import org.jetbrains.compose.web.css.maxWidth
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.overflowX
import web.cssom.atrule.maxWidth

object WorldDocsStyles : StyleSheet() {
    // Indentation helpers for tree levels
    val indent0 by style { marginLeft(0.px) }
    val indent1 by style { marginLeft(24.px) }
    val indent2 by style { marginLeft(48.px) }
    val indent3 by style { marginLeft(72.px) }
    val indent4 by style { marginLeft(96.px) }
    val indent5 by style { marginLeft(120.px) }
    val indent6 by style { marginLeft(144.px) }

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
        padding(16.px)
        marginBottom(16.px)
        borderRadius(16.px)
        backgroundColor(Theme.colors.background)
        overflowX("auto")
        flexWrap(FlexWrap.Wrap)

        dark(self) {
            backgroundColor(Theme.colors.backgroundDark)
        }
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
        maxWidth(140.px)
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
        borderRadius(12.px)
        cursor("pointer")
        color(Theme.colors.onSurface)
        boxSizing("border-box")
        // Make row a positioned container so drop-zone overlays can be absolutely positioned
        property("position", "relative")
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
        borderRadius(12.px)
        cursor("pointer")
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
        boxSizing("border-box")
        // Keep positioned container in selected state as well
        property("position", "relative")
    }

    // Row actions container for selected item (e.g., inline create child)
    val rowActions by style {
        property("position", "absolute")
        property("right", "8px")
        property("top", "50%")
        property("transform", "translateY(-50%)")
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(6.px)
        // above DnD overlays
        property("z-index", "3")
    }
    val rowActionButton by style {
        cursor("pointer")
        color(Theme.colors.onSurfaceVariant)
        fontSize(14.px)
        padding(4.px)
        borderRadius(6.px)
        self + hover style {
            backgroundColor(Theme.colors.surfaceVariant)
        }
        dark(self) {
            color(Theme.colors.onSurfaceVariantDark)
            self + hover style {
                backgroundColor(Theme.colors.surfaceVariantDark)
            }
        }
    }

    // Right panel: top controls row (rename/delete)
    val controlsRow by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.FlexStart)
        gap(8.px)
        marginBottom(16.px)
        overflowX("auto")
        flexWrap(FlexWrap.Wrap)
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
    // Overlay zones: absolutely positioned within each row so they don't affect layout
    val dropZone by style {
        // thin overlay band used for BEFORE/AFTER targets when not active
        height(4.px)
        backgroundColor(Color.transparent)
        borderRadius(4.px)
        property("position", "absolute")
        property("left", "0")
        property("right", "0")
        property("z-index", "2")
        // ensure the band can receive drag events while present
        property("pointer-events", "auto")
    }
    val dropZoneActive by style {
        height(4.px)
        backgroundColor(Theme.colors.accentInfoSoft)
        borderRadius(4.px)
        property("position", "absolute")
        property("left", "0")
        property("right", "0")
        property("z-index", "2")
        property("pointer-events", "auto")
    }
    // Full-row INTO overlay (visual only)
    val dropIntoOverlay by style {
        property("position", "absolute")
        property("top", "0")
        property("bottom", "0")
        property("left", "0")
        property("right", "0")
        borderRadius(12.px)
        border(4.px, LineStyle.Solid, Theme.colors.accentInfoSoft)
        // Place under edge bands; visual only
        property("z-index", "1")
        // Do not intercept any events; row/edge handlers remain active
        property("pointer-events", "none")
    }
    // Position helpers for overlay zones
    val dropZoneTop by style {
        property("top", "0")
    }
    val dropZoneBottom by style {
        property("bottom", "0")
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

    // History list and items
    val historyList by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(12.px)
        overflowY("auto")
        minHeight(0.px)
    }

    val historyItem by style {
        backgroundColor(Theme.colors.surface)
        border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtle)
        borderRadius(8.px)
        padding(12.px)
        dark(self) {
            backgroundColor(Theme.colors.surfaceDark)
            border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtleDark)
        }
    }

    val historyHeader by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(org.jetbrains.compose.web.css.JustifyContent.SpaceBetween)
        gap(8.px)
        marginBottom(8.px)
    }

    val historyUsers by style {
        color(Theme.colors.onSurface)
        dark(self) { color(Theme.colors.onSurfaceDark) }
        fontSize(12.px)
    }

    val historyTimestamp by style {
        color(Theme.colors.textSecondary)
        fontSize(12.px)
    }

    // Diff block
    val diffBlock by style {
        borderRadius(6.px)
        border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtle)
        backgroundColor(Theme.colors.surfaceVariant)
        padding(8.px)
        property("font-family", "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, \"Liberation Mono\", \"Courier New\", monospace")
        property("white-space", "pre-wrap")
        property("overflow-x", "auto")
        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            border(1.px, LineStyle.Solid, Theme.colors.panelBorderSubtleDark)
        }
    }

    val diffAdd by style {
        backgroundColor(Theme.colors.successSoftBg)
        color(Theme.colors.onSurface)
        borderRadius(4.px)
        padding(1.px, 6.px)
        dark(self) {
            color(Theme.colors.onSurfaceDark)
        }
    }

    val diffRemove by style {
        backgroundColor(Theme.colors.accentWarnSoft)
        color(Theme.colors.onSurface)
        borderRadius(4.px)
        padding(1.px, 6.px)
        dark(self) {
            color(Theme.colors.onSurfaceDark)
        }
    }

    val diffHeader by style {
        color(Theme.colors.textSecondary)
        padding(1.px, 6.px)
        dark(self) { color(Theme.colors.textSecondaryDark) }
    }

    val diffContext by style {
        padding(1.px, 6.px)
        color(Theme.colors.onSurface)
        dark(self) { color(Theme.colors.onSurfaceDark) }
    }

    // Spacing above the Restore button row
    val historyRestoreRow by style {
        marginTop(8.px)
    }
}
