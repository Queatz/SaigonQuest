package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.InventoryApi
import main.api.ItemApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Translated
import main.components.translated
import Translations.str
import main.theme.Styles
import models.InventoryItem
import models.Item
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
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
    var assetIndex by remember { mutableStateOf("0") }
    var wearable by remember { mutableStateOf(false) }
    var wearableFile by remember { mutableStateOf("") }

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
        
        isSubmitting = true
        errorMessage = ""
        
        coroutineScope.launch {
            try {
                // Create the item
                val newItem = Item(
                    title = title.trim(),
                    description = description.trim(),
                    file = file.trim(),
                    assetIndex = assetIndex.toInt(),
                    wearable = wearable,
                    wearableFile = if (wearable) wearableFile.trim() else null,
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
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        padding(16.px)
                    }
                }
            ) {
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
                                color(Color.white)
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
                                color(Color.white)
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
                
                // File field
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
                                color(Color.white)
                                marginBottom(4.px)
                            }
                        }
                    ) {
                        Translated("File path (URL to sprite sheet)")
                    }
                    
                    val filePlaceholder = translated("Enter file path")
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder(filePlaceholder)
                            value(file)
                            onInput { file = it.value }
                        }
                    )
                    
                    if (fileError.isNotEmpty()) {
                        P(
                            attrs = {
                                style {
                                    color(rgba(255, 100, 100, 0.9))
                                    fontSize(12.px)
                                    marginTop(4.px)
                                }
                            }
                        ) {
                            Translated(fileError)
                        }
                    }
                }
                
                // Asset Index field
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
                                color(Color.white)
                                marginBottom(4.px)
                            }
                        }
                    ) {
                        Translated("Asset index (0-99)")
                    }
                    
                    val assetIndexPlaceholder = translated("Enter asset index")
                    Input(
                        type = InputType.Number,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder(assetIndexPlaceholder)
                            value(assetIndex)
                            onInput { 
                                // Convert Number? to String
                                assetIndex = it.value?.toString() ?: "0"
                            }
                            attr("min", "0")
                            attr("max", "99")
                        }
                    )
                    
                    if (assetIndexError.isNotEmpty()) {
                        P(
                            attrs = {
                                style {
                                    color(rgba(255, 100, 100, 0.9))
                                    fontSize(12.px)
                                    marginTop(4.px)
                                }
                            }
                        ) {
                            Translated(assetIndexError)
                        }
                    }
                }
                
                // Wearable checkbox
                Div(
                    attrs = {
                        style {
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                marginBottom(4.px)
                            }
                        }
                    ) {
                        Input(
                            type = InputType.Checkbox,
                            attrs = {
                                checked(wearable)
                                onChange { event -> 
                                    wearable = event.target.checked
                                }
                                style {
                                    marginTop(4.px)
                                }
                            }
                        )
                        
                        Label(
                            attrs = {
                                style {
                                    color(Color.white)
                                    marginBottom(4.px)
                                    marginTop(2.px)
                                    padding(0.px, 0.px, 0.px, 8.px)
                                }
                            }
                        ) {
                            Translated("Is this item wearable?")
                        }
                    }
                }
                
                // Conditional wearable fields
                if (wearable) {
                    // Wearable File field
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
                                    color(Color.white)
                                    marginBottom(4.px)
                                }
                            }
                        ) {
                            Translated("Wearable file path (URL to sprite sheet)")
                        }
                        
                        val wearablePlaceholder = translated("Enter wearable file path")
                        Input(
                            type = InputType.Text,
                            attrs = {
                                classes(Styles.inputField)
                                placeholder(wearablePlaceholder)
                                value(wearableFile)
                                onInput { wearableFile = it.value }
                            }
                        )
                        
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
}
