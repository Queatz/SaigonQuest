package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.NumberInput
import main.theme.Styles
import web.cssom.AtRules.Companion.property

/**
 * Reusable 5x5 numeric grid editor for wind weights (values in [0,1]).
 * - grid: 5x5 list of strings representing numeric values
 * - onGridChange: callback with updated grid when any cell changes
 * - showReset: whether to render a Reset to defaults button
 * - onReset: optional reset handler; if null and showReset=true, a default reset pattern is used
 */
@Composable
fun WeightsGridEditor(
    grid: List<List<String>>,
    onGridChange: (List<List<String>>) -> Unit,
    showReset: Boolean = true,
    onReset: (() -> Unit)? = null,
) {
    // Reset button row (optional)
    if (showReset) {
        Div(attrs = { style { marginBottom(8.px) } }) {
            DialogButton(
                onClick = {
                    if (onReset != null) {
                        onReset()
                    } else {
                        // Default gradient-like values top->bottom
                        val rv = listOf("0", "0.0125", "0.025", "0.5", "1")
                        val def = List(5) { j -> List(5) { rv[j] } }
                        onGridChange(def)
                    }
                },
                isPrimary = false,
            ) { Translated("Reset to defaults") }
        }
    }

    // 5x5 grid inputs
    Div(
        attrs = {
            style {
                display(DisplayStyle.Grid)
                property("grid-template-columns", "repeat(5, 64px)")
                property("gap", "6px")
                marginBottom(16.px)
            }
        }
    ) {
        for (j in 0 until 5) {
            for (i in 0 until 5) {
                DecimalNumberInput(
                    value = grid[j][i],
                    onChange = { v ->
                        val newGrid = grid.mapIndexed { r, row ->
                            if (r != j) row else row.mapIndexed { c, cell -> if (c == i) v else cell }
                        }
                        onGridChange(newGrid)
                    }
                )
            }
        }
    }
}
