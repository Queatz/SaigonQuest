package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ItemApi
import main.components.AssetBrowserDialog
import main.components.BaseDialog
import main.components.DialogButton
import main.components.IconRenderer
import main.components.LabeledCheckbox
import main.components.Translated
import main.components.translated
import main.theme.Styles
import models.AssetType
import models.Item
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.NumberInput
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.TextInput

@Composable
fun EditItemDialog(
    item: Item,
    onClose: () -> Unit,
    onSaved: (Item) -> Unit,
) {
    // Prefilled form state
    var title by remember { mutableStateOf(item.title) }
    var description by remember { mutableStateOf(item.description) }
    var file by remember { mutableStateOf(item.file) }
    var cost by remember { mutableStateOf(item.cost) }
    var assetIndex by remember { mutableStateOf(item.assetIndex.toString()) }
    var wearable by remember { mutableStateOf(item.wearable) }
    var wearableFile by remember { mutableStateOf(item.wearableFile ?: "") }
    var customizeWeights by remember { mutableStateOf(!item.wearableWeights.isNullOrBlank()) }

    fun parseWeightsCsv(csv: String?): List<List<String>> {
        if (csv.isNullOrBlank()) return List(5) { List(5) { "" } }
        val parts = csv.split(",")
        val padded = if (parts.size >= 25) parts.take(25) else parts + List(25 - parts.size) { "0" }
        val grid = mutableListOf<List<String>>()
        var idx = 0
        for (j in 0 until 5) {
            val row = mutableListOf<String>()
            for (i in 0 until 5) {
                row.add(padded[idx].trim())
                idx++
            }
            grid.add(row)
        }
        return grid
    }

    var weightGrid by remember { mutableStateOf(parseWeightsCsv(item.wearableWeights)) }

    var showAssetBrowser by remember { mutableStateOf(false) }
    var showWearableBrowser by remember { mutableStateOf(false) }

    // Validation state
    var titleError by remember { mutableStateOf("") }
    var fileError by remember { mutableStateOf("") }
    var assetIndexError by remember { mutableStateOf("") }
    var wearableFileError by remember { mutableStateOf("") }

    // Loading state
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    fun validateForm(): Boolean {
        var isValid = true
        if (title.trim().isEmpty()) { titleError = "Title is required"; isValid = false } else { titleError = "" }
        if (file.trim().isEmpty()) { fileError = "File path is required"; isValid = false } else { fileError = "" }
        try {
            val index = assetIndex.toInt()
            if (index < 0) { assetIndexError = "Asset index must be a positive number"; isValid = false } else { assetIndexError = "" }
        } catch (_: Throwable) {
            assetIndexError = "Asset index must be a number"; isValid = false
        }
        if (wearable) {
            if (wearableFile.trim().isEmpty()) { wearableFileError = "Wearable file path is required"; isValid = false } else { wearableFileError = "" }
        }
        return isValid
    }

    fun handleSubmit() {
        if (!validateForm()) return

        var weightsCsv: String? = null
        if (wearable && customizeWeights) {
            val nums = mutableListOf<String>()
            for (j in 0 until 5) {
                for (i in 0 until 5) {
                    val s = weightGrid[j][i].trim()
                    if (s.isEmpty()) {
                        nums.add("0")
                    } else {
                        val v = s.toDoubleOrNull() ?: run {
                            errorMessage = "Wind weights are invalid"; return
                        }
                        val clamped = v.coerceIn(0.0, 1.0)
                        nums.add(clamped.toString())
                    }
                }
            }
            if (nums.size == 25) {
                weightsCsv = nums.joinToString(",")
            }
        }

        isSubmitting = true
        errorMessage = ""

        coroutineScope.launch {
            try {
                val updatedItem = Item(
                    title = title.trim(),
                    description = description.trim(),
                    file = file.trim(),
                    cost = cost,
                    assetIndex = assetIndex.toInt(),
                    wearable = wearable,
                    wearableFile = if (wearable) wearableFile.trim() else null,
                    wearableWeights = if (wearable) weightsCsv else null,
                ).also { it.key = item.key }

                val saved = ItemApi.updateItem(item.key, updatedItem)
                onSaved(saved)
                onClose()
            } catch (t: Throwable) {
                console.error("Error updating item", t)
                errorMessage = t.message ?: "Failed to update item"
                isSubmitting = false
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Edit item"),
        content = {
            if (errorMessage.isNotEmpty()) {
                P(attrs = { style { color(rgba(255, 100, 100, 0.9)); marginBottom(16.px) } }) { Translated(errorMessage) }
            }

            // Title
            Div(attrs = { style { marginBottom(16.px) } }) {
                Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Title") }
                val titlePlaceholder = translated("Enter item title")
                TextInput(
                    value = title,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(titlePlaceholder)
                        onInput { title = it.value }
                    }
                )
                if (titleError.isNotEmpty()) {
                    P(attrs = { style { color(rgba(255, 100, 100, 0.9)); fontSize(12.px); marginTop(4.px) } }) { Translated(titleError) }
                }
            }

            // Description
            Div(attrs = { style { marginBottom(16.px) } }) {
                Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Description") }
                val descriptionPlaceholder = translated("Enter item description")
                TextArea(attrs = {
                    classes(Styles.inputField)
                    placeholder(descriptionPlaceholder)
                    value(description)
                    onInput { description = it.value }
                    style { height(80.px) }
                })
            }

            // Asset selection
            Div(attrs = { style { marginBottom(16.px) } }) {
                Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Asset") }

                // Preview of selected asset (if any)
                if (file.isNotBlank()) {
                    val idx = assetIndex.toIntOrNull() ?: 0
                    IconRenderer(
                        url = "/raw/data/spritesheets/$file.png",
                        index = idx,
                        size = 48,
                    )
                } else {
                    P { Translated("No results") }
                }

                DialogButton(
                    onClick = { showAssetBrowser = true },
                    isPrimary = false,
                ) { Translated("Browse assets") }
            }

            // Cost
            Div(attrs = { style { marginBottom(16.px) } }) {
                Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Cost (VND)") }
                NumberInput(value = cost, min = 0, attrs = {
                    classes(Styles.inputField)
                    onInput { cost = it.value?.toLong() ?: 0L }
                })
            }

            // Wearable toggle
            Div(attrs = { style { marginBottom(16.px) } }) {
                LabeledCheckbox(
                    checked = wearable,
                    enabled = true,
                    label = translated("Is this item wearable?"),
                    onCheckedChange = { next: Boolean -> wearable = next },
                )
            }

            if (wearable) {
                // Wearable asset (preview + browser)
                Div(attrs = { style { marginBottom(16.px) } }) {
                    Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Wearable asset") }

                    // Preview first 32x48 cell if set
                    if (wearableFile.isNotBlank()) {
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Block)
                                marginTop(8.px)
                            }
                        }) {
                            Div(attrs = {
                                classes(Styles.iconRenderer)
                                style {
                                    property("width", "32px")
                                    property("height", "48px")
                                    property("background-image", "url(\"/raw/data/spritesheets/$wearableFile.png\")")
                                    property("background-size", "${32 * 8}px ${48 * 8}px")
                                    property("background-position", "0px 0px")
                                }
                            }) {}
                        }
                    }

                    // Browse button for wearable sprite sheet
                    DialogButton(
                        onClick = { showWearableBrowser = true },
                        isPrimary = false,
                    ) { Translated("Browse assets") }

                    if (wearableFileError.isNotEmpty()) {
                        P(
                            attrs = {
                                style {
                                    color(rgba(255, 100, 100, 0.9))
                                    fontSize(12.px)
                                    marginTop(4.px)
                                }
                            }
                        ) {
                            Translated(wearableFileError)
                        }
                    }
                }

                // Customize weights checkbox
                Div(attrs = { style { marginBottom(8.px); display(DisplayStyle.Flex); property("align-items", "center") } }) {
                    LabeledCheckbox(
                        checked = customizeWeights,
                        enabled = true,
                        label = translated("Customize wind weights"),
                        onCheckedChange = { next -> customizeWeights = next },
                    )
                }

                if (customizeWeights) {
                    Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Wind weights (5x5)") }

                    Div(attrs = { style { marginBottom(8.px) } }) {
                        DialogButton(onClick = {
                            val rv = listOf("0", "0.0125", "0.025", "0.5", "1")
                            weightGrid = List(5) { j -> List(5) { rv[j] } }
                        }, isPrimary = false) { Translated("Reset to defaults") }
                    }

                    Div(attrs = { style { display(DisplayStyle.Grid); property("grid-template-columns", "repeat(5, 64px)"); property("gap", "6px"); marginBottom(16.px) } }) {
                        for (j in 0 until 5) {
                            for (i in 0 until 5) {
                                NumberInput(value = weightGrid[j][i].toDoubleOrNull(), attrs = {
                                    classes(Styles.inputField)
                                    onInput { ev ->
                                        val v = ev.value?.toString() ?: ""
                                        weightGrid = weightGrid.mapIndexed { r, row ->
                                            if (r != j) row else row.mapIndexed { c, cell -> if (c == i) v else cell }
                                        }
                                    }
                                    attr("min", "0")
                                    attr("max", "1")
                                    attr("step", "0.0125")
                                    style { property("width", "64px"); property("padding", "4px") }
                                })
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Cancel") }
            DialogButton(onClick = { handleSubmit() }, isPrimary = true) {
                if (isSubmitting) Translated("Saving...") else Translated("Save")
            }
        }
    )

    if (showAssetBrowser) {
        AssetBrowserDialog(
            type = AssetType.Items,
            onSelect = { r ->
                file = r.asset!!.file
                assetIndex = (r.index ?: 0).toString()
                showAssetBrowser = false
            },
            onClose = { showAssetBrowser = false },
        )
    }

    if (showWearableBrowser) {
        AssetBrowserDialog(
            type = AssetType.SpriteSheet,
            onSelect = { r ->
                wearableFile = r.asset!!.file
                showWearableBrowser = false
            },
            onClose = { showWearableBrowser = false },
        )
    }
}
