package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import main.api.InventoryApi
import main.api.ItemApi
import main.api.AssetApi
import main.api.AssetItemApi
import models.AssetType
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.translated
import main.components.IconRenderer
import main.components.AssetBrowserDialog
import main.components.LabeledCheckbox
import Translations.str
import main.theme.Styles
import models.InventoryItem
import models.Item
import models.ItemWearableLocation
import models.Sex
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.Color
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
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

/**
 * A dialog for creating new items.
 * 
 * @param onClose Callback when the dialog is closed
 * @param onItemCreated Callback when an item is successfully created
 */
@Composable
fun CreateItemDialog(
    onClose: () -> Unit,
    onItemCreated: (Item) -> Unit
) {
    // Form state
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var file by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf(0L) }
    var assetIndex by remember { mutableStateOf("0") }
    var wearable by remember { mutableStateOf(false) }
    var wearableFile by remember { mutableStateOf("") }
    var wearableSex by remember { mutableStateOf(Sex.Boy) }
    var wearableLocation by remember { mutableStateOf(ItemWearableLocation.Body) }
    var customizeWeights by remember { mutableStateOf(false) }
    var weightGrid by remember { mutableStateOf(List(5) { List(5) { "" } }) }
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
    
    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Prefill wind weights from asset items when wearable file changes
    LaunchedEffect(wearable, wearableFile) {
        try {
            if (!wearable) return@LaunchedEffect
            val fileName = wearableFile.trim()
            if (fileName.isEmpty()) return@LaunchedEffect
            // Avoid overriding if user already enabled custom weights
            if (customizeWeights) return@LaunchedEffect
            // Find the spritesheet asset matching the filename (without extension)
            val spriteAssets = AssetApi.getAssetsByType(AssetType.SpriteSheet)
            val matched = spriteAssets.firstOrNull { it.file.equals(fileName, ignoreCase = true) }
            if (matched != null) {
                val items = AssetItemApi.getSpriteSheetAssetItemsByAsset(matched.key)
                val cfg = items.firstOrNull { it.customWeights && !it.weightsCsv.isNullOrBlank() }
                val csv = cfg?.weightsCsv
                if (!csv.isNullOrBlank()) {
                    fun parseCsv(input: String): List<List<String>>? {
                        val parts = input.split(',', ' ', ';', '\n', '\t')
                            .mapNotNull { s -> s.trim().takeIf { it.isNotEmpty() } }
                        if (parts.size < 25) return null
                        val nums = parts.take(25).mapNotNull { it.toDoubleOrNull() }
                        if (nums.size != 25) return null
                        val asStrings = nums.map { it.coerceIn(0.0, 1.0).toString() }
                        // Row-major 5x5
                        return List(5) { r -> List(5) { c -> asStrings[r * 5 + c] } }
                    }
                    val grid = parseCsv(csv)
                    if (grid != null) {
                        customizeWeights = true
                        weightGrid = grid
                    }
                }
            }
        } catch (t: Throwable) {
            console.warn("Failed to prefill wearable weights from asset:", t.message)
        }
    }
    
    // Function to validate the form
    fun validateForm(): Boolean {
        var isValid = true
        
        // Validate title
        if (title.trim().isEmpty()) {
            titleError = "Title is required"
            isValid = false
        } else {
            titleError = ""
        }
        
        // Validate file
        if (file.trim().isEmpty()) {
            fileError = "File path is required"
            isValid = false
        } else {
            fileError = ""
        }
        
        // Validate asset index
        try {
            val index = assetIndex.toInt()
            if (index < 0) {
                assetIndexError = "Asset index must be a positive number"
                isValid = false
            } else {
                assetIndexError = ""
            }
        } catch (e: NumberFormatException) {
            assetIndexError = "Asset index must be a number"
            isValid = false
        }
        
        // Validate wearable fields if wearable is checked
        if (wearable) {
            // Validate wearable file
            if (wearableFile.trim().isEmpty()) {
                wearableFileError = "Wearable file path is required"
                isValid = false
            } else {
                wearableFileError = ""
            }
        }
        
        return isValid
    }
    
    // Function to handle form submission
    fun handleSubmit() {
        if (!validateForm()) {
            return
        }

        // If customizing weights, build CSV (row-major, 25 values). Validate numeric.
        var weightsCsv: String? = null
        if (wearable && customizeWeights) {
            // Convert grid to numbers, allowing blanks to be 0
            val nums = mutableListOf<String>()
            loop@ for (j in 0 until 5) {
                for (i in 0 until 5) {
                    val s = weightGrid[j][i].trim()
                    if (s.isEmpty()) {
                        nums.add("0")
                    } else {
                        val v = s.toDoubleOrNull()
                        if (v == null) {
                            errorMessage = "Wind weights are invalid"
                            return
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
                // Create the item
                val newItem = Item(
                    title = title.trim(),
                    description = description.trim(),
                    file = file.trim(),
                    cost = cost,
                    assetIndex = assetIndex.toInt(),
                    wearable = wearable,
                    wearableFile = if (wearable) wearableFile.trim() else null,
                    wearableSex = if (wearable) wearableSex else null,
                    wearableLocation = if (wearable) wearableLocation else null,
                    wearableWeights = if (wearable) weightsCsv else null,
                )
                
                val createdItem = ItemApi.createItem(newItem)
                
                // Add 10 copies of the item to the user's inventory
                val inventoryItem = InventoryItem(
                    quantity = 10,
                    item = createdItem.key
                )
                
                InventoryApi.addItemToInventory(inventoryItem)
                
                // Call the callback
                onItemCreated(createdItem)
                
                // Close the dialog
                onClose()
            } catch (error: Throwable) {
                console.error("Error creating item", error)
                errorMessage = error.message ?: "Failed to create item"
                isSubmitting = false
            }
        }
    }
    
    BaseDialog(
        onClose = onClose,
        title = translated("Create new item"),
        content = {
            // Error message
            if (errorMessage.isNotEmpty()) {
                P(
                    attrs = {
                        style {
                            color(rgba(255, 100, 100, 0.9))
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Translated(errorMessage)
                }
            }
            
            // Title field
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            ) {
                Label(
                    attrs = {
                        style {
                            display(DisplayStyle.Block)

                            marginBottom(4.px)
                        }
                    }
                ) {
                    Translated("Title")
                }
                
                val titlePlaceholder = translated("Enter item title")
                Input(
                    type = InputType.Text,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(titlePlaceholder)
                        value(title)
                        onInput { title = it.value }
                    }
                )
                
                if (titleError.isNotEmpty()) {
                    P(
                        attrs = {
                            style {
                                color(rgba(255, 100, 100, 0.9))
                                fontSize(12.px)
                                marginTop(4.px)
                            }
                        }
                    ) {
                        Translated(titleError)
                    }
                }
            }
            
            // Description field
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            ) {
                Label(
                    attrs = {
                        style {
                            display(DisplayStyle.Block)

                            marginBottom(4.px)
                        }
                    }
                ) {
                    Translated("Description")
                }
                
                val descriptionPlaceholder = translated("Enter item description")
                TextArea(
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(descriptionPlaceholder)
                        value(description)
                        onInput { description = it.value }
                        style {
                            height(80.px)
                        }
                    }
                )
            }
            
            // Asset selection
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            ) {
                Label(
                    attrs = {
                        style {
                            display(DisplayStyle.Block)

                            marginBottom(4.px)
                        }
                    }
                ) { Translated("Asset") }

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

            
            // Cost field
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            ) {
                Label(
                    attrs = {
                        style {
                            display(DisplayStyle.Block)

                            marginBottom(4.px)
                        }
                    }
                ) {
                    Translated("Cost (VND)")
                }

                NumberInput(
                    value = cost,
                    min = 0,
                    attrs = {
                        classes(Styles.inputField)
                        onInput { cost = it.value?.toLong() ?: 0L }
                    }
                )
            }
            
            
            // Wearable checkbox
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            ) {
                LabeledCheckbox(
                    checked = wearable,
                    enabled = true,
                    label = translated("Is this item wearable?"),
                    onCheckedChange = { next: Boolean -> wearable = next },
                )
            }
            
            // Conditional wearable fields
            if (wearable) {
                // Wearable sex
                Div(
                    attrs = {
                        style {
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Label(
                        attrs = {
                            style {
                                display(DisplayStyle.Block)
                                marginBottom(4.px)
                            }
                        }
                    ) {
                        Translated("Sex")
                    }
                    Select(attrs = {
                        classes(Styles.inputField)
                        onInput { ev ->
                            try {
                                wearableSex = Sex.valueOf(ev.value ?: Sex.Boy.name)
                            } catch (_: Throwable) {}
                        }
                    }) {
                        Sex.entries.forEach { s ->
                            Option(s.name, attrs = { if (s == wearableSex) selected() }) { Translated(s.name) }
                        }
                    }
                }

                // Wearable location
                Div(
                    attrs = {
                        style {
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Label(
                        attrs = {
                            style {
                                display(DisplayStyle.Block)
                                marginBottom(4.px)
                            }
                        }
                    ) {
                        Translated("Location")
                    }
                    Select(attrs = {
                        classes(Styles.inputField)
                        onInput { ev ->
                            try {
                                wearableLocation = ItemWearableLocation.valueOf(ev.value ?: ItemWearableLocation.Body.name)
                            } catch (_: Throwable) {}
                        }
                    }) {
                        ItemWearableLocation.entries.forEach { loc ->
                            Option(loc.name, attrs = { if (loc == wearableLocation) selected() }) { Translated(loc.name) }
                        }
                    }
                }

                // Wearable File field + browser/preview
                Div(
                    attrs = {
                        style {
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Label(
                        attrs = {
                            style {
                                display(DisplayStyle.Block)

                                marginBottom(4.px)
                            }
                        }
                    ) {
                        Translated("Wearable asset")
                    }

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

                // Customize wind weights toggle
                Div(
                    attrs = {
                        style {
                            marginBottom(8.px)
                            display(DisplayStyle.Flex)
                            property("align-items", "center")
                        }
                    }
                ) {
                    LabeledCheckbox(
                        checked = customizeWeights,
                        enabled = true,
                        label = translated("Customize wind weights"),
                        onCheckedChange = { next -> customizeWeights = next },
                    )
                }

                if (customizeWeights) {
                    // Controls for 5x5 weights
                    Label(
                        attrs = {
                            style {
                                display(DisplayStyle.Block)

                                marginBottom(4.px)
                            }
                        }
                    ) { Translated("Wind weights (5x5)") }

                    main.components.WeightsGridEditor(
                        grid = weightGrid,
                        onGridChange = { weightGrid = it },
                        showReset = true,
                    )
                }
            }
            
            // Help text
            P(
                attrs = {
                    style {
                        color(rgba(200, 200, 200, 0.8))
                        fontSize(12.px)
                        marginTop(8.px)
                    }
                }
            ) {
                Translated("Note: creating an item will add 10 copies of it to your inventory.")
            }
        },
        actions = {
            // Cancel button
            DialogButton(
                onClick = onClose
            ) {
                Translated("Cancel")
            }
            
            // Create button
            DialogButton(
                onClick = { handleSubmit() },
                isPrimary = true
            ) {
                if (isSubmitting) {
                    Translated("Creating...")
                } else {
                    Translated("Create")
                }
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
