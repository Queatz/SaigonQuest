package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.AssetApi
import main.api.AssetItemApi
import main.theme.Styles
import models.Asset
import models.AssetType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.dom.*

@Composable
fun AdminAssetsDialog(
    onClose: () -> Unit,
) {
    var assets by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var assetCategories by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var assetTitles by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<AssetType?>(null) }

    val scope = rememberCoroutineScope()

    fun loadAssets() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val fetchedAssets = AssetApi.getAllAssets()
                assets = fetchedAssets

                // Fetch categories and titles
                val categoriesMap = mutableMapOf<String, MutableSet<String>>()
                val titlesMap = mutableMapOf<String, MutableSet<String>>()
                AssetType.entries.forEach { type ->
                    try {
                        val results = AssetItemApi.searchWithAsset(type, null, null)
                        results.forEach { result ->
                            val assetId = result.asset?.key
                            val cat = result.category
                            val title = result.title
                            if (assetId != null) {
                                if (!cat.isNullOrBlank()) {
                                    categoriesMap.getOrPut(assetId) { mutableSetOf() }.add(cat)
                                }
                                if (!title.isNullOrBlank()) {
                                    titlesMap.getOrPut(assetId) { mutableSetOf() }.add(title)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        console.error("Failed to fetch categories and titles for type $type", t)
                    }
                }
                assetCategories = categoriesMap
                assetTitles = titlesMap
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

    val searchPlaceholder = translated("Search assets")
    val allTypesLabel = translated("All types")

    val headerActions: @Composable () -> Unit = {
        DialogButton(
            onClick = { showRegisterAssetDialog = true },
            style = DialogButtonStyle.Primary
        ) { Translated("Register new asset") }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Assets"),
        headerActions = headerActions,
        content = {
            if (errorMessage != null) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(errorMessage!!) }
            }

            // Search and filter
            Div(attrs = { classes(Styles.adminAssetsHeaderBar) }) {
                TextInput(
                    value = searchQuery,
                    attrs = {
                        classes(Styles.inputField, Styles.adminAssetsSearchInput)
                        placeholder(searchPlaceholder)
                        onInput { searchQuery = it.value }
                    }
                )

                Select(
                    attrs = {
                        classes(Styles.inputField, Styles.adminAssetsTypeFilter)
                        onChange { ev ->
                            selectedType = AssetType.entries.firstOrNull { it.name == ev.value }
                        }
                    }
                ) {
                    Option(value = "") { Text(allTypesLabel) }
                    AssetType.entries.forEach { type ->
                        Option(
                            value = type.name,
                            attrs = {
                                if (type == selectedType) selected()
                            }
                        ) { Text(type.name) }
                    }
                }
            }

            val filteredAssets = assets.filter { asset ->
                val matchesType = selectedType == null || asset.type == selectedType
                val matchesSearch = searchQuery.isBlank() || asset.file.contains(searchQuery, ignoreCase = true) ||
                        (assetCategories[asset.key]?.any { it.contains(searchQuery, ignoreCase = true) } == true) ||
                        (assetTitles[asset.key]?.any { it.contains(searchQuery, ignoreCase = true) } == true)
                matchesType && matchesSearch
            }

            when {
                isLoading -> P { Translated("Loading...") }
                filteredAssets.isEmpty() -> P { Translated("No assets found") }
                else -> {
                    // Header
                    Div(attrs = { classes(Styles.adminAssetsGridHeader) }) {
                        Span {} // Icon
                        Span { Translated("Type") }
                        Span { Translated("File") }
                        Span { Translated("Title") }
                        Span { Translated("Categories") }
                        Span {} // Actions
                    }

                    // Rows
                    for (asset in filteredAssets) {
                        Div(attrs = { classes(Styles.adminAssetsGridRow) }) {
                            // Icon
                            val iconIndex = when (asset.type) {
                                AssetType.Items -> 2
                                AssetType.SpriteSheet -> 3
                                AssetType.World -> 0
                                AssetType.Objects -> 1
                            }
                            IconRenderer(
                                url = "/raw/data/ui/admin.png",
                                index = iconIndex,
                            )

                            Span { Text(asset.type.name) }
                            Span {
                                if (asset.file.isBlank()) {
                                    Translated("(untitled)")
                                } else {
                                    Text(asset.file)
                                }
                            }
                            val titles = assetTitles[asset.key] ?: emptySet()
                            // Titles
                            Span({
                                style {
                                    if (titles.isEmpty()) {
                                        opacity(0.5f)
                                    }
                                }
                            }) {
                                if (titles.isEmpty()) {
                                    Text("-")
                                } else {
                                    Text(titles.joinToString(", "))
                                }
                            }
                            val cats = assetCategories[asset.key] ?: emptySet()
                            // Categories
                            Span({
                                style {
                                    if (cats.isEmpty()) {
                                        opacity(0.5f)
                                    }
                                }
                            }) {
                                if (cats.isEmpty()) {
                                    Text("-")
                                } else {
                                    Text(cats.joinToString(", "))
                                }
                            }
                            // Actions
                            Span(
                                {
                                    style {
                                        textAlign("right")
                                    }
                                }
                            ) {
                                DialogButton(
                                    onClick = {
                                        selectedAsset = asset
                                        showEditDialog = true
                                    },
                                    style = DialogButtonStyle.Primary
                                ) { Translated("Edit") }
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
            initialType = selectedType,
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
