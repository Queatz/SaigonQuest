package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.AssetApi
import main.theme.Styles
import models.Asset
import main.components.Translated
import main.components.translated
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun AdminAssetsDialog(
    onClose: () -> Unit,
) {
    var assets by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun loadAssets() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                assets = AssetApi.getAllAssets()
            } catch (t: Throwable) {
                console.error("Failed to load assets", t)
                errorMessage = t.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadAssets() }

    var showRegisterAssetDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedAsset by remember { mutableStateOf<Asset?>(null) }

    val headerActions: @Composable () -> Unit = {
        Button(attrs = {
            classes(Styles.baseDialogButtonPrimary)
            onClick { showRegisterAssetDialog = true }
        }) { Translated("Register new asset") }

        Button(attrs = {
            classes(Styles.baseDialogButtonSecondary)
            onClick { loadAssets() }
        }) { Translated("Refresh") }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Assets"),
        headerActions = headerActions,
        maxWidth = "720px",
        content = {
            if (errorMessage != null) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(errorMessage!!) }
            }

            when {
                isLoading -> P { Translated("Loading...") }
                assets.isEmpty() -> P { Translated("No assets found") }
                else -> {
                    // Header row
                    Div(attrs = { classes(Styles.assetsGridHeader) }) {
                        Span { Translated("Type") }
                        Span { Translated("File") }
                        Span { Translated("Actions") }
                    }
                    // Rows
                    for (asset in assets) {
                        Div(attrs = { classes(Styles.assetsGridRow) }) {
                            Span { Text(asset.type.name) }
                            Span {
                                if (asset.file.isBlank()) {
                                    Translated("(untitled)")
                                } else {
                                    Text(asset.file)
                                }
                            }
                            // Actions
                            Span {
                                Button(attrs = {
                                    classes(Styles.baseDialogButtonSecondary)
                                    onClick {
                                        selectedAsset = asset
                                        showEditDialog = true
                                    }
                                }) { Translated("Edit") }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )

    if (showRegisterAssetDialog) {
        AdminRegisterAssetDialog(
            onClose = { showRegisterAssetDialog = false },
            onRegisterAsset = { asset ->
                scope.launch {
                    try {
                        errorMessage = null
                        isLoading = true
                        val created = AssetApi.createAsset(asset)
                        // Immediately open edit dialog for the created asset
                        selectedAsset = created
                        showEditDialog = true
                    } catch (t: Throwable) {
                        console.error("Failed to create asset", t)
                        errorMessage = t.message ?: "Failed to create asset"
                    } finally {
                        isLoading = false
                        loadAssets()
                    }
                }
            },
        )
    }

    if (showEditDialog && selectedAsset != null) {
        AdminEditAssetDialog(
            asset = selectedAsset!!,
            onClose = { showEditDialog = false },
            onDeleted = {
                showEditDialog = false
                loadAssets()
            },
        )
    }
}
