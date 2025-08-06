package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.Styles
import models.Asset
import models.AssetType
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

/**
 * A dialog for admin operations
 * 
 * @param onClose Callback when the dialog is closed
 * @param onRegisterAsset Callback when an asset is registered
 */
@Composable
fun AdminDialog(
    onClose: () -> Unit,
    onRegisterAsset: (Asset) -> Unit
) {
    // State for the asset registration form
    var selectedAssetType by remember { mutableStateOf(AssetType.Items) }
    var assetFileName by remember { mutableStateOf("") }
    
    // Use the BaseDialog component
    BaseDialog(
        onClose = onClose,
        title = "Admin Panel",
        showCloseButton = true,
        content = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        property("gap", "16px")
                        width(100.percent)
                    }
                }
            ) {
                // Asset registration form
                H3 {
                    Text("Register New Asset")
                }
                
                // Asset type selection
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            width(100.percent)
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Text("Asset Type")
                    Select(
                        attrs = {
                            style {
                                width(100.percent)
                                padding(8.px)
                                margin(8.px, 0.px)
                            }
                            onInput { event ->
                                val value = event.value
                                if (value != null) {
                                    selectedAssetType = AssetType.valueOf(value)
                                }
                            }
                        }
                    ) {
                        AssetType.entries.forEach { type ->
                            Option(
                                value = type.name,
                                attrs = {
                                    if (type == selectedAssetType) {
                                        attr("selected", "true")
                                    }
                                }
                            ) {
                                Text(type.name)
                            }
                        }
                    }
                }
                
                // Asset filename input
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            width(100.percent)
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Text("File Name (without extension)")
                    TextInput(
                        value = assetFileName,
                        attrs = {
                            classes(Styles.inputField)

                            onInput { event ->
                                assetFileName = event.value
                            }
                        }
                    )
                }
            }
        },
        actions = {
            // Register asset button
            DialogButton(
                onClick = {
                    if (assetFileName.isNotBlank()) {
                        val asset = Asset(
                            type = selectedAssetType,
                            file = assetFileName
                        )
                        onRegisterAsset(asset)
                        onClose()
                    }
                },
                isPrimary = true
            ) {
                Text("Register Asset")
            }
            
            // Close button
            DialogButton(
                onClick = onClose,
                isPrimary = false
            ) {
                Text("Close")
            }
        }
    )
}
