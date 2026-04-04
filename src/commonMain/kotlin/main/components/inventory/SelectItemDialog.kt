package main.components.inventory

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ItemApi
import main.components.*
import main.theme.Styles
import models.Item
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.min
import org.jetbrains.compose.web.attributes.step
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.NumberInput
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import web.cssom.AtRules.Companion.property

/**
 * SelectItemDialog
 * - Supports single or multiple selection
 * - Optional quantities per selected item
 * - Simple local search filter by title
 */
@Composable
fun SelectItemDialog(
    initial: List<Pair<Item, Int>> = emptyList(),
    multiple: Boolean,
    useQuantities: Boolean,
    onConfirm: (List<Pair<Item, Int>>) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<Item>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }

    // Selected map: item.key -> Pair<Item, qty>
    var selected by remember(initial) {
        mutableStateOf(
            initial.associateBy({ it.first.key }, { it })
        )
    }

    fun loadItems() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                items = ItemApi.getAllItems()
            } catch (t: Throwable) {
                console.error("Failed to load items", t)
                errorMessage = t.message ?: "Failed to load items"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadItems() }

    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) items else items.filter { it.title.lowercase().contains(q) || it.key.lowercase().contains(q) }
    }

    BaseDialog(
        onClose = onClose,
        title = if (multiple) translated("Select items") else translated("Select item"),
        maxWidth = "860px",
        content = {
            Div(attrs = {
                style {
                    property("display", "flex")
                    property("gap", "8px")
                    property("align-items", "center")
                }
            }) {
                DialogInput(
                    value = query,
                    onInput = { query = it },
                    placeholder = str("Search by title"),
                )
            }

            if (errorMessage != null) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(errorMessage!!) }
            }

            when {
                isLoading -> P { Translated("Loading...") }
                filtered.isEmpty() -> P { Translated("No results") }
                else -> {
                    // Simple grid/list
                    Div(attrs = {
                        style {
                            property("display", "grid")
                            property("grid-template-columns", "repeat(auto-fill, minmax(180px, 1fr))")
                            property("gap", "12px")
                            property("margin-top", "12px")
                            property("max-height", "50vh")
                            property("overflow-y", "auto")
                        }
                    }) {
                        for (item in filtered) {
                            val isSelected = selected.containsKey(item.key)

                            fun toggleSelected() {
                                selected = if (multiple) {
                                    if (selected.containsKey(item.key)) {
                                        selected - item.key
                                    } else {
                                        selected + (item.key to (item to 0))
                                    }
                                } else {
                                    if (isSelected) {
                                        emptyMap()
                                    } else {
                                        mapOf(item.key to (item to 0))
                                    }
                                }
                            }

                            Div(attrs = {
                                style {
                                    property("padding", if (isSelected) "10px" else "11px")
                                    property("border", if (isSelected) "2px solid rgba(0, 120, 255, 0.7)" else "1px solid rgba(255,255,255,0.1)")
                                    property("border-radius", "8px")
                                    cursor("pointer")
                                }

                                onClick {
                                    toggleSelected()
                                }
                            }) {
                                // Preview
                                Div {
                                    ItemRenderer(item = item, size = 48, onClick = {
                                        if (multiple) {
                                            selected = if (isSelected) {
                                                selected - item.key
                                            } else {
                                                selected + (item.key to (item to 0))
                                            }
                                        } else {
                                            selected = mapOf(item.key to (item to 0))
                                        }
                                    })
                                }
                                // Title
                                P { Text(item.title.ifBlank { "(${translated("No item selected")})" }) }

                                // Select/Clear button
                                Div(attrs = {
                                    style {
                                        property("display", "flex")
                                        property("gap", "8px")
                                        property("align-items", "center")
                                    }
                                }) {
                                    DialogButton(
                                        onClick = {
                                            toggleSelected()
                                        },
                                        style = DialogButtonStyle.Primary,
                                        attrs = {
                                            onClick { it.stopPropagation() }
                                        }
                                    ) { Text(if (isSelected) translated("Clear") else translated("Select")) }

                                    if (useQuantities && isSelected) {
                                        val current = selected[item.key] ?: (item to 0)
                                        // Quantity input
                                        NumberInput(
                                            value = current.second.toLong(),
                                            min = 0,
                                            attrs = {
                                                classes(Styles.inputField)
                                                onInput { ev ->
                                                    val v = (ev.value?.toLong() ?: 0L).coerceAtLeast(0L)
                                                    selected = selected + (item.key to (item to v.toInt()))
                                                }

                                                onClick {
                                                    it.stopPropagation()
                                                }
                                            }
                                        )
                                        Label { Translated("Quantity") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
            DialogButton(onClick = {
                val result = selected.values.toList()
                onConfirm(result)
                onClose()
            }, style = DialogButtonStyle.Primary) { Translated("Confirm") }
        }
    )
}
