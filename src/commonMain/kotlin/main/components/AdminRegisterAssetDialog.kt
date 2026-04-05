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
import org.jetbrains.compose.web.dom.*

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
            DialogSelect(
                options = AssetType.entries.map { it.name to it.name },
                value = selectedAssetType.name,
                onInput = { v ->
                    selectedAssetType = AssetType.valueOf(v)
                    lastSelectedAssetType = selectedAssetType
                },
                label = "Asset type",
                containerAttrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            )

            // Asset filename input
            DialogInput(
                value = assetFileName,
                onInput = { assetFileName = it },
                label = "File name (without extension)",
                containerAttrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            )
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
