package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.Styles
import models.Asset
import models.AssetType
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

/**
 * Dialog for registering a new asset.
 */
private var lastSelectedAssetType: AssetType = AssetType.Items

@Composable
fun AdminRegisterAssetDialog(
    onClose: () -> Unit,
    onRegisterAsset: (Asset) -> Unit,
    initialType: AssetType? = null,
) {
    var selectedAssetType by remember { mutableStateOf(initialType ?: lastSelectedAssetType) }
    var assetFileName by remember { mutableStateOf("") }

    BaseDialog(
        onClose = onClose,
        title = translated("Register new asset"),
        showCloseButton = true,
        content = {
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
                Translated("Asset type")
                Select(
                    attrs = {
                        classes(Styles.inputField)
                        onInput { event ->
                            val value = event.value
                            if (value != null) {
                                selectedAssetType = AssetType.valueOf(value)
                                lastSelectedAssetType = selectedAssetType
                            }
                        }
                    }
                ) {
                    AssetType.entries.forEach { type ->
                        Option(
                            value = type.name,
                            attrs = {
                                if (type == selectedAssetType) {
                                    selected()
                                }
                            }
                        ) { Text(type.name) }
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
                Translated("File name (without extension)")
                TextInput(
                    value = assetFileName,
                    attrs = {
                        classes(Styles.inputField)
                        onInput { event -> assetFileName = event.value }
                    }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose, style = DialogButtonStyle.Secondary) { Translated("Close") }
            DialogButton(
                onClick = {
                    if (assetFileName.isNotBlank()) {
                        val asset = Asset(
                            type = selectedAssetType,
                            file = assetFileName,
                        )
                        onRegisterAsset(asset)
                        onClose()
                    }
                },
                style = DialogButtonStyle.Primary,
            ) { Translated("Register asset") }
        }
    )
}
