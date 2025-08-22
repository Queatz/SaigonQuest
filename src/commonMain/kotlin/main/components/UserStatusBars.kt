package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span

/**
 * Renders the user's Health, Mind, and Spirit status bars using the UI sprite sheet.
 * Each bar is a 0..10 integer representing 5 icons with half steps.
 *
 * Indices (in /raw/data/ui/ui.png):
 * - Health: 10 (full), 11 (half), 12 (empty)
 * - Mind:   13 (full), 14 (half), 15 (empty)
 * - Spirit: 16 (full), 17 (half), 18 (empty)
 */
@Composable
fun UserStatusBars(
    health: Int,
    mind: Int,
    spirit: Int,
    readOnly: Boolean = true,
    onChange: ((health: Int, mind: Int, spirit: Int) -> Unit)? = null,
    size: Int = 24,
) {
    val sheetUrl = "/raw/data/ui/ui.png"

    fun iconIndexFor(value: Int, position: Int, indices: Triple<Int, Int, Int>): Int {
        val remaining = value - position * 2
        return when {
            remaining >= 2 -> indices.first
            remaining == 1 -> indices.second
            else -> indices.third
        }
    }

    // Two-column layout: right-aligned labels on the left, left-aligned icon rows on the right
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            alignItems(AlignItems.FlexStart)
            gap(12.px)
        }
    }) {
        // Labels column
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                alignItems(AlignItems.FlexEnd)
                gap(8.px)
            }
        }) {
            Span(
                attrs = {
                    classes(Styles.playerAttrStatusHeader)
                }
            ) { Translated("Health") }
            Span(
                attrs = {
                    classes(Styles.playerAttrStatusHeader)
                }
            ) { Translated("Mind") }
            Span(
                attrs = {
                    classes(Styles.playerAttrStatusHeader)
                }
            ) { Translated("Spirit") }
        }

        // Icons column
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                alignItems(AlignItems.FlexStart)
                gap(8.px)
            }
        }) {
            // Row: Health
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    gap(8.px)
                }
            }) {
                repeat(5) { i ->
                    val idx = iconIndexFor(health.coerceIn(0, 10), i, Triple(10, 11, 12))
                    IconRenderer(
                        url = sheetUrl,
                        index = idx,
                        size = size,
                        onClick = if (readOnly || onChange == null) null else {
                            {
                                val full = (i + 1) * 2
                                val half = i * 2 + 1
                                val empty = i * 2
                                val newVal = when {
                                    health >= full && health < full + 2 -> half
                                    health == half -> empty
                                    else -> full
                                }.coerceIn(0, 10)
                                onChange.invoke(newVal, mind, spirit)
                            }
                        }
                    )
                }
            }

            // Row: Mind
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    gap(8.px)
                }
            }) {
                repeat(5) { i ->
                    val idx = iconIndexFor(mind.coerceIn(0, 10), i, Triple(13, 14, 15))
                    IconRenderer(
                        url = sheetUrl,
                        index = idx,
                        size = size,
                        onClick = if (readOnly || onChange == null) null else {
                            {
                                val full = (i + 1) * 2
                                val half = i * 2 + 1
                                val empty = i * 2
                                val newVal = when {
                                    mind >= full && mind < full + 2 -> half
                                    mind == half -> empty
                                    else -> full
                                }.coerceIn(0, 10)
                                onChange.invoke(health, newVal, spirit)
                            }
                        }
                    )
                }
            }

            // Row: Spirit
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    alignItems(AlignItems.Center)
                    gap(8.px)
                }
            }) {
                repeat(5) { i ->
                    val idx = iconIndexFor(spirit.coerceIn(0, 10), i, Triple(16, 17, 18))
                    IconRenderer(
                        url = sheetUrl,
                        index = idx,
                        size = size,
                        onClick = if (readOnly || onChange == null) null else {
                            {
                                val full = (i + 1) * 2
                                val half = i * 2 + 1
                                val empty = i * 2
                                val newVal = when {
                                    spirit >= full && spirit < full + 2 -> half
                                    spirit == half -> empty
                                    else -> full
                                }.coerceIn(0, 10)
                                onChange.invoke(health, mind, newVal)
                            }
                        }
                    )
                }
            }
        }
    }
}
