package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import lib.Color3
import lib.Scene
import main.components.world.WorldLoader
import main.api.UserApi
import main.api.WorldApi
import main.api.CostsApi
import main.api.BalanceApi
import main.components.common.formatAmount
import main.theme.Styles
import models.User
import models.World
import models.canUserEdit
import models.AssetType
import main.api.ItemApi
import main.api.AssetItemApi
import main.api.SavedPositionApi
import main.components.objects.startCreatePlacementMode
import main.components.objects.cancelCreatePlacementMode
import models.SavedPosition
import models.Position
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.RangeInput
import org.jetbrains.compose.web.dom.Text
import app.softwork.routingcompose.Router
import main.components.world.LatLngPicker3D
import main.components.DialogInput
import Translations.str
import main.components.friends.SelectFriendDialog
import models.Item
import main.components.inventory.SelectItemDialog
import main.components.media.UploadMediaDialog
import models.MediaType
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.width

/**
 * Content component for the world edit form.
 *
 * @param currentWorld The world being edited, or null if creating a new world
 * @param onClose Callback when the form is closed
 * @param onSave Callback when a world is saved (created or updated)
 */
@Composable
fun WorldEditContent(
    currentWorld: World? = null,
    onClose: () -> Unit,
    onSave: (World) -> Unit,
) {
    /**
     * Updates the scene background color from a hex color string.
     *
     * @param hexColor The hex color string (e.g., "#FF0000" for red)
     */
    fun updateSceneBackgroundColor(hexColor: String) {
        try {
            // Get the scene from SceneManager
            val scene = SceneManager.getScene()

            if (scene != null) {
                // Convert hex color string to Color3
                val color3 = Color3.FromHexString(hexColor)

                // Convert Color3 to Color4 with alpha=1 (fully opaque)
                val color4 = color3.toColor4()
                color4.a = 1.0f

                // Set the scene's clear color
                scene.clearColor = color4.toLinearSpace()

                // Sync hemispheric light color to match background
                WorldLoader.updateHemisphericLightColorHex(hexColor)

                scene.fogColor = color3
            } else {
                println("[ERROR] Cannot update background color: Scene is null")
            }
        } catch (e: Throwable) {
            println("[ERROR] Failed to update background color: ${e.message}")
        }
    }

    /**
     * Updates the scene fog density.
     *
     * @param density The fog density value (0.0 to 1.0)
     */
    fun updateSceneFogDensity(density: Float) {
        try {
            // Get the scene from SceneManager
            val scene = SceneManager.getScene()

            // Get the rendering pipeline from the CameraController
            val cameraController = SceneManager.getCameraController()
            val pipeline = cameraController?.getPipeline()

            if (scene != null) {
                if (density > 0.0f) {
                    // Enable fog in the scene
                    scene.fogMode = Scene.FOGMODE_EXP2

                    // Scale the density for better visual effect (0.0-1.0 to 0.0-0.1)
                    scene.fogDensity = density * 0.1f

                    // Enable fog in the rendering pipeline
                    pipeline?.fogEnabled = true

                    println("[DEBUG] Updated scene fog density to $density and enabled fog in pipeline")
                } else {
                    // Disable fog by setting density to 0
                    scene.fogDensity = 0.0f

                    // Disable fog in the rendering pipeline
                    pipeline?.fogEnabled = false

                    println("[DEBUG] Disabled scene fog and disabled fog in pipeline")
                }
            } else {
                println("[ERROR] Cannot update fog density: Scene is null")
            }
        } catch (e: Throwable) {
            println("[ERROR] Failed to update fog density: ${e.message}")
        }
    }

    val router = Router.current
    val defaultUrl = router.currentPath.path.trimStart('/')
    // State for form fields
    var title by remember { mutableStateOf(currentWorld?.title ?: "") }
    var subtitle by remember { mutableStateOf(currentWorld?.subtitle ?: "") }
    var description by remember { mutableStateOf(currentWorld?.description ?: "") }
    var url by remember { mutableStateOf(currentWorld?.url ?: defaultUrl) }
    var file by remember { mutableStateOf(currentWorld?.file ?: "") }
    var assetId by remember { mutableStateOf(currentWorld?.assetId ?: "") }
    var backgroundColor by remember { mutableStateOf(currentWorld?.backgroundColor ?: "#ffffff") }
    var backgroundColorIntensity by remember { mutableStateOf(currentWorld?.backgroundColorIntensity ?: 1.0f) }
    var directionalLightColor by remember { mutableStateOf(currentWorld?.directionalLightColor ?: "#ffffff") }
    var directionalLightIntensity by remember { mutableStateOf(currentWorld?.directionalLightIntensity ?: 1.0f) }
    var fogDensity by remember { mutableStateOf(currentWorld?.fogDensity ?: 0.0f) }
    var screenshot by remember { mutableStateOf(currentWorld?.screenshot) }
    var isUploadingScreenshot by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    // Music
    var musicUrl by remember { mutableStateOf(currentWorld?.musicUrl) }
    var musicTitle by remember { mutableStateOf(currentWorld?.musicTitle) }
    var nightMusicUrl by remember { mutableStateOf(currentWorld?.nightMusicUrl) }
    var nightMusicTitle by remember { mutableStateOf(currentWorld?.nightMusicTitle) }
    var showMusicDialog by remember { mutableStateOf(false) }
    var showNightMusicDialog by remember { mutableStateOf(false) }
    // Chat history
    var chatLongevityEnabled by remember { mutableStateOf((currentWorld?.chatLongevity ?: 0.0) > 0.0) }
    var chatLongevityDays by remember {
        mutableStateOf(
            currentWorld?.chatLongevity?.takeIf { it > 0.0 }?.toString() ?: ""
        )
    }
    // Outdoors/time-of-day
    var isOutdoors by remember { mutableStateOf(currentWorld?.isOutdoors ?: false) }
    var latitude by remember { mutableStateOf(currentWorld?.latLng?.getOrNull(0)?.toString() ?: "") }
    var longitude by remember { mutableStateOf(currentWorld?.latLng?.getOrNull(1)?.toString() ?: "") }
    var showWorldAssetBrowser by remember { mutableStateOf(false) }
    var selectedWorldAssetTitle by remember { mutableStateOf<String?>(null) }

    // Access control state
    var allowAllVisitors by remember { mutableStateOf(currentWorld?.allowAllVisitors ?: true) }
    var allowAllPlayers by remember { mutableStateOf(currentWorld?.allowAllPlayers ?: false) }
    var allowFriendsOfOwners by remember { mutableStateOf(currentWorld?.allowFriendsOfOwners ?: false) }
    var allowHoldersOfItemId by remember { mutableStateOf(currentWorld?.allowHoldersOfItemId) }
    var showAccessItemBrowser by remember { mutableStateOf(false) }
    var accessItemError by remember { mutableStateOf<String?>(null) }

    // Owners state
    var ownerIds by remember { mutableStateOf(currentWorld?.ownerIds ?: emptyList()) }
    var ownerNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var primaryOwnerName by remember { mutableStateOf<String?>(null) }
    var showOwnerPicker by remember { mutableStateOf(false) }
    var ownerIdPendingRemoval by remember { mutableStateOf<String?>(null) }
    
    // Saved positions state
    var savedPositions by remember { mutableStateOf(emptyList<SavedPosition>()) }
    var isLoadingSavedPositions by remember { mutableStateOf(false) }
    var isSavedPositionPlacementActive by remember { mutableStateOf(false) }
    var showSavedPositionNameDialog by remember { mutableStateOf(false) }
    var pendingSavedPosition by remember { mutableStateOf<Position?>(null) }
    var savedPositionError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val spm = SceneManager.getSavedPositionManager()
        spm?.setShowSavedPositions(true)
        onDispose {
            spm?.setShowSavedPositions(false)
            if (isSavedPositionPlacementActive) {
                SceneManager.getObjectManager()?.cancelCreatePlacementMode()
            }
        }
    }

    // Fetch saved positions
    LaunchedEffect(currentWorld?.key) {
        val worldId = currentWorld?.key ?: return@LaunchedEffect
        isLoadingSavedPositions = true
        try {
            savedPositions = SavedPositionApi.getByWorldId(worldId)
            SceneManager.getSavedPositionManager()?.setSavedPositions(savedPositions)
        } catch (e: Throwable) {
            console.error("Failed to load saved positions", e)
        } finally {
            isLoadingSavedPositions = false
        }
    }

    // Re-seed local state when the incoming currentWorld changes
    LaunchedEffect(currentWorld?.key) {
        title = currentWorld?.title ?: ""
        subtitle = currentWorld?.subtitle ?: ""
        description = currentWorld?.description ?: ""
        url = currentWorld?.url ?: defaultUrl
        file = currentWorld?.file ?: ""
        assetId = currentWorld?.assetId ?: ""
        selectedWorldAssetTitle = null
        backgroundColor = currentWorld?.backgroundColor ?: "#ffffff"
        backgroundColorIntensity = currentWorld?.backgroundColorIntensity ?: 1.0f
        directionalLightColor = currentWorld?.directionalLightColor ?: "#ffffff"
        directionalLightIntensity = currentWorld?.directionalLightIntensity ?: 1.0f
        fogDensity = currentWorld?.fogDensity ?: 0.0f
        // Music
        musicUrl = currentWorld?.musicUrl
        musicTitle = currentWorld?.musicTitle
        nightMusicUrl = currentWorld?.nightMusicUrl
        nightMusicTitle = currentWorld?.nightMusicTitle
        // Chat
        chatLongevityEnabled = (currentWorld?.chatLongevity ?: 0.0) > 0.0
        chatLongevityDays = currentWorld?.chatLongevity?.takeIf { it > 0.0 }?.toString() ?: ""
        // Outdoors/time-of-day
        isOutdoors = currentWorld?.isOutdoors ?: false
        latitude = currentWorld?.latLng?.getOrNull(0)?.toString() ?: ""
        longitude = currentWorld?.latLng?.getOrNull(1)?.toString() ?: ""
        allowAllVisitors = currentWorld?.allowAllVisitors ?: true
        allowAllPlayers = currentWorld?.allowAllPlayers ?: false
        allowFriendsOfOwners = currentWorld?.allowFriendsOfOwners ?: false
        allowHoldersOfItemId = currentWorld?.allowHoldersOfItemId
        // Owners
        ownerIds = currentWorld?.ownerIds ?: emptyList()

    }

    LaunchedEffect(currentWorld?.key, assetId) {
        if (assetId.isNotBlank()) {
            try {
                val items = AssetItemApi.getWorldAssetItemsByAsset(assetId)
                selectedWorldAssetTitle = items.firstOrNull()?.title
            } catch (t: Throwable) {
                console.warn("[WARN] Failed to load world asset title by assetId: ${t.message}")
            }
        }
    }

    // Load owner display names
    LaunchedEffect(currentWorld?.userId, ownerIds) {
        try {
            val primaryId = currentWorld?.userId
            if (!primaryId.isNullOrBlank()) {
                try {
                    primaryOwnerName = UserApi.getUserById(primaryId)?.username ?: primaryId
                } catch (_: Throwable) {
                    primaryOwnerName = primaryId
                }
            } else {
                primaryOwnerName = null
            }
            val map = mutableMapOf<String, String>()
            ownerIds.forEach { id ->
                if (id.isNotBlank()) {
                    try {
                        val u = UserApi.getUserById(id)
                        map[id] = u?.username ?: id
                    } catch (_: Throwable) {
                        map[id] = id
                    }
                }
            }
            ownerNames = map
        } catch (_: Throwable) {
            // ignore
        }
    }

    // Enforce initial checkbox behavior per rules
    LaunchedEffect(Unit) {
        if (allowAllVisitors) {
            if (!allowAllPlayers) allowAllPlayers = true
            if (!allowFriendsOfOwners) allowFriendsOfOwners = true
            if (allowHoldersOfItemId == null) allowHoldersOfItemId = ""
        } else if (allowAllPlayers) {
            if (!allowFriendsOfOwners) allowFriendsOfOwners = true
            if (allowHoldersOfItemId == null) allowHoldersOfItemId = ""
        }
    }

    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // State for current user
    var currentUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Pricing and balance state
    var worldBasePrice by remember { mutableStateOf<Long?>(null) }
    var myBalance by remember { mutableStateOf<Long?>(null) }
    var showConfirmPurchase by remember { mutableStateOf(false) }

    // Determine if we're editing or creating
    val isEditing = currentWorld != null
    val isCreating = !isEditing

    // Create a coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()

    // Fetch current user
    LaunchedEffect(Unit) {
        isLoading = true

        try {
            val user = UserApi.getCurrentUser()
            currentUser = user
            isLoading = false
        } catch (error: Throwable) {
            console.error("Error fetching current user", error)
            isLoading = false
        }
    }

    // Fetch costs and current balance
    LaunchedEffect(Unit) {
        try {
            val c = CostsApi.getCosts()
            worldBasePrice = c.worldBasePrice
        } catch (t: Throwable) {
            console.warn("[WARN] Failed to load costs: ${t.message}")
        }
        try {
            val b = BalanceApi.getBalance()
            myBalance = b.balance
        } catch (t: Throwable) {
            console.warn("[WARN] Failed to load balance: ${t.message}")
        }
    }

    // Determine ownership and edit permissions
    val isOwner = if (isEditing && currentUser != null) {
        currentWorld.userId == currentUser?.key
    } else {
        // If creating a new world or user not loaded yet, default to true
        true
    }
    val canEditWorld = if (isEditing && currentUser != null) {
        currentWorld.canUserEdit(currentUser!!.key)
    } else {
        true
    }

    // Determine if form fields should be enabled
    val fieldsEnabled = !isEditing || canEditWorld

    // Snapshot the active world at editor open to allow restore on cancel
    val initialActiveWorldSnapshot = remember { WorldLoader.getActiveWorld() }
    // Track whether a save occurred
    var didSave by remember { mutableStateOf(false) }

    // Function to restore original lighting/state
    fun restoreOriginalState() {
        try {
            // If editing and the file was changed during preview, reload the original file
            if (isEditing && file != currentWorld.file) {
                WorldLoader.reloadWorldFile(currentWorld)
                // Also ensure lighting is restored to the original world values
                WorldLoader.notifyWorldEdited(currentWorld)
                return
            }

            // If creating (not editing) and we previewed a file, reload the original active world file if available
            if (!isEditing && initialActiveWorldSnapshot != null) {
                WorldLoader.reloadWorldFile(initialActiveWorldSnapshot)
                WorldLoader.notifyWorldEdited(initialActiveWorldSnapshot)
                return
            }

            // Otherwise, just restore lighting to whatever was originally active
            if (currentWorld != null) {
                WorldLoader.notifyWorldEdited(currentWorld)
            } else {
                if (initialActiveWorldSnapshot != null) {
                    WorldLoader.notifyWorldEdited(initialActiveWorldSnapshot)
                } else {
                    WorldLoader.reapplyCurrentWorld()
                }
            }
        } catch (t: Throwable) {
            console.warn("[WARN] Failed to restore original world state: ${t.message}")
        }
    }

    // Ensure restore on unmount if user closes without saving
    DisposableEffect(Unit) {
        onDispose {
            if (!didSave) {
                restoreOriginalState()
            }
        }
    }

    // Function to handle form submission
    val handleSubmit = submit@{
        isSubmitting = true
        errorMessage = ""

        // Validate chat longevity input if enabled
        val chatLongevityValue: Double = if (chatLongevityEnabled) {
            val parsed = chatLongevityDays.toDoubleOrNull()
            if (parsed == null || parsed.isNaN() || parsed <= 0.0) {
                isSubmitting = false
                errorMessage = str("Please enter a positive number of days")
                return@submit
            }
            parsed
        } else 0.0

        val worldModel = if (isEditing) {
            // Update existing world
            currentWorld.copy(
                title = title,
                subtitle = subtitle,
                description = description,
                url = url,
                assetId = assetId,
                file = file,
                isOutdoors = isOutdoors,
                latLng = run {
                    val lat = latitude.toDoubleOrNull()
                    val lon = longitude.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(
                        lat,
                        lon
                    ) else null
                },
                backgroundColor = backgroundColor,
                backgroundColorIntensity = backgroundColorIntensity,
                directionalLightColor = directionalLightColor,
                directionalLightIntensity = directionalLightIntensity,
                fogDensity = fogDensity,
                screenshot = screenshot,
                musicUrl = musicUrl,
                musicTitle = musicTitle,
                nightMusicUrl = nightMusicUrl,
                nightMusicTitle = nightMusicTitle,
                // Access control
                allowAllVisitors = allowAllVisitors,
                allowAllPlayers = allowAllPlayers,
                allowFriendsOfOwners = allowFriendsOfOwners,
                allowHoldersOfItemId = allowHoldersOfItemId?.takeIf { it.isNotBlank() },
                // Owners
                ownerIds = ownerIds,
                // Chat settings
                chatLongevity = chatLongevityValue,
            )
        } else {
            // Create new world
            World(
                title = title,
                subtitle = subtitle,
                description = description,
                url = url,
                assetId = assetId,
                file = file,
                isOutdoors = isOutdoors,
                latLng = run {
                    val lat = latitude.toDoubleOrNull()
                    val lon = longitude.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(
                        lat,
                        lon
                    ) else null
                },
                backgroundColor = backgroundColor,
                backgroundColorIntensity = backgroundColorIntensity,
                directionalLightColor = directionalLightColor,
                directionalLightIntensity = directionalLightIntensity,
                fogDensity = fogDensity,
                screenshot = screenshot,
                musicUrl = musicUrl,
                musicTitle = musicTitle,
                nightMusicUrl = nightMusicUrl,
                nightMusicTitle = nightMusicTitle,
                allowAllVisitors = allowAllVisitors,
                allowAllPlayers = allowAllPlayers,
                allowFriendsOfOwners = allowFriendsOfOwners,
                allowHoldersOfItemId = allowHoldersOfItemId?.takeIf { it.isNotBlank() },
                chatLongevity = chatLongevityValue,
            )
        }

        coroutineScope.launch {
            try {
                val fileChanged = isEditing && currentWorld.file != worldModel.file
                val savedWorld = if (isEditing) {
                    WorldApi.updateWorld(currentWorld.key, worldModel)
                } else {
                    WorldApi.createWorld(worldModel)
                }

                // Update the scene to reflect edited world
                if (fileChanged) {
                    // Reload the world asset if the file changed
                    WorldLoader.reloadWorldFile(savedWorld)
                } else {
                    // Only lighting changed
                    WorldLoader.notifyWorldEdited(savedWorld)
                }

                isSubmitting = false
                didSave = true
                onSave(savedWorld)
                onClose()
            } catch (error: Throwable) {
                isSubmitting = false
                errorMessage = error.message ?: "An error occurred"
            }
        }
    }

    // Helper to preview lighting immediately when editing
    fun previewLightingFromForm() {
        try {
            val lat = latitude.toDoubleOrNull()
            val lon = longitude.toDoubleOrNull()
            val latLngList =
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(lat, lon) else null
            val previewWorld = currentWorld?.copy(
                // Only fields relevant to lighting are necessary
                isOutdoors = isOutdoors,
                latLng = latLngList,
                backgroundColor = backgroundColor,
                backgroundColorIntensity = backgroundColorIntensity,
                directionalLightColor = directionalLightColor,
                directionalLightIntensity = directionalLightIntensity,
                fogDensity = fogDensity,
            ) ?: return
            WorldLoader.notifyWorldEdited(previewWorld)
        } catch (e: Throwable) {
            console.warn("[WARN] Failed to preview lighting from form: ${e.message}")
        }
    }

    // Error message banner (fixed at top)
    if (errorMessage.isNotEmpty()) {
        key(errorMessage) {
            Div(
                attrs = {
                    classes(Styles.formErrorBanner)
                }
            ) {
                Text("⚠️ ")
                Translated(errorMessage)
            }
        }
    }

    // Form content
    Div(
        attrs = {
            style {
                property("flex-grow", "1")
                padding(16.px)
                property("overflow-y", "auto")
                property("overflow-x", "hidden")
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                property("gap", "16px")
            }
        }
    ) {
        // Ownership message
        if (isEditing && !canEditWorld) {
            P(
                attrs = {
                    style {

                        backgroundColor(rgba(0, 0, 0, 0.3))
                        padding(8.px)
                        borderRadius(8.px)
                        margin(0.px)
                    }
                }
            ) {
                Translated("You don't own this world.")
            }
        }

        // Owners management
        if (isEditing) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        property("gap", "8px")
                    }
                }
            ) {
                Label(attrs = { style { fontSize(14.px) } }) { Translated("Owners") }

                // Other owners list
                if (ownerIds.isNotEmpty()) {
                    ownerIds.forEach { oid ->
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                justifyContent(JustifyContent.SpaceBetween)
                                gap(8.px)
                                alignItems(AlignItems.Center)
                            }
                        }) {
                            Div(
                                attrs = {
                                    style {
                                        width(0.px)
                                        flexGrow(1)
                                        property("overflow", "hidden")
                                        property("text-overflow", "ellipsis")

                                    }
                                }
                            ) {
                                Text(ownerNames[oid] ?: oid)
                            }
                            if (isOwner) {
                                DialogButton(
                                    onClick = {
                                        ownerIdPendingRemoval = oid
                                    },
                                    style = DialogButtonStyle.Secondary
                                ) {
                                    Translated("Remove owner")
                                }
                            }
                        }
                    }
                } else {
                    P(attrs = { style { margin(0.px); color(rgba(255, 255, 255, 0.6)) } }) {
                        Translated("No owners")
                    }
                }

                // Add owner controls (primary owner only)
                if (isOwner) {
                    Div(attrs = { style { display(DisplayStyle.Flex); property("gap", "8px") } }) {
                        DialogButton(
                            onClick = { showOwnerPicker = true },
                            style = DialogButtonStyle.Primary
                        ) {
                            Translated("Add owner")
                        }
                    }
                }

                if (showOwnerPicker) {
                    SelectFriendDialog(
                        onSelect = { f ->
                            val id = f.friendId
                            if (id.isNotBlank() && id != currentWorld.userId && !ownerIds.contains(id)) {
                                ownerIds = ownerIds + id
                                // Optimistically update display name map
                                ownerNames = ownerNames + (id to (f.friendUsername.ifBlank { id }))
                            }
                            showOwnerPicker = false
                        },
                        onClose = { showOwnerPicker = false },
                        excludeIds = buildSet {
                            currentWorld.userId.let { if (it.isNotBlank()) add(it) }
                            addAll(ownerIds)
                        },
                    )
                }

                if (ownerIdPendingRemoval != null) {
                    val oidToRemove = ownerIdPendingRemoval!!
                    BasicConfirmDialog(
                        message = translated("Remove owner"),
                        onConfirm = {
                            ownerIds = ownerIds.filter { it != oidToRemove }
                            ownerIdPendingRemoval = null
                        },
                        onCancel = { ownerIdPendingRemoval = null },
                    )
                }
            }
        }

        // Saved positions management
        if (isEditing) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        property("gap", "8px")
                    }
                }
            ) {
                Label(attrs = { style { fontSize(14.px) } }) { Translated("Saved positions") }

                if (savedPositions.isNotEmpty()) {
                    savedPositions.forEach { pos ->
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                justifyContent(JustifyContent.SpaceBetween)
                                gap(8.px)
                                alignItems(AlignItems.Center)
                            }
                        }) {
                            Div(
                                attrs = {
                                    style {
                                        width(0.px)
                                        flexGrow(1)
                                        property("overflow", "hidden")
                                        property("text-overflow", "ellipsis")
                                    }
                                }
                            ) {
                                Text(pos.name)
                            }
                            
                            DialogButton(
                                onClick = {
                                    SceneManager.getCameraController()?.focusOnPosition(
                                        lib.Vector3(pos.position.x, pos.position.y, pos.position.z)
                                    )
                                },
                                style = DialogButtonStyle.Secondary
                            ) {
                                Translated("Locate")
                            }

                            DialogButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            SavedPositionApi.delete(pos.key)
                                            savedPositions = savedPositions.filter { it.key != pos.key }
                                            SceneManager.getSavedPositionManager()?.setSavedPositions(savedPositions)
                                        } catch (e: Throwable) {
                                            savedPositionError = e.message
                                        }
                                    }
                                },
                                style = DialogButtonStyle.Secondary
                            ) {
                                Translated("Delete")
                            }
                        }
                    }
                } else if (!isLoadingSavedPositions) {
                    P(attrs = { style { margin(0.px); color(rgba(255, 255, 255, 0.6)) } }) {
                        Translated("No saved positions")
                    }
                }

                if (isOwner) {
                    DialogButton(
                        onClick = {
                            val objectManager = SceneManager.getObjectManager()
                            if (isSavedPositionPlacementActive) {
                                objectManager?.cancelCreatePlacementMode()
                                isSavedPositionPlacementActive = false
                            } else {
                                isSavedPositionPlacementActive = true
                                objectManager?.startCreatePlacementMode(
                                    onConfirm = { vec: lib.Vector3 ->
                                        isSavedPositionPlacementActive = false
                                        pendingSavedPosition = Position(vec.x, vec.y, vec.z)
                                        showSavedPositionNameDialog = true
                                    },
                                    onCancel = {
                                        isSavedPositionPlacementActive = false
                                    }
                                )
                            }
                        },
                        style = DialogButtonStyle.Primary
                    ) {
                        if (isSavedPositionPlacementActive) Translated("Cancel")
                        else Translated("Add saved position")
                    }
                }

                if (showSavedPositionNameDialog) {
                    BasicInputDialog(
                        title = translated("Add saved position"),
                        placeholder = str("Saved position name"),
                        onConfirm = { name ->
                            val pos = pendingSavedPosition
                            if (pos != null && name.isNotBlank()) {
                                coroutineScope.launch {
                                    try {
                                        val newPos = SavedPositionApi.create(
                                            SavedPosition(
                                                name = name,
                                                worldId = currentWorld.key,
                                                position = pos
                                            )
                                        )
                                        savedPositions = savedPositions + newPos
                                        SceneManager.getSavedPositionManager()?.setSavedPositions(savedPositions)
                                    } catch (e: Throwable) {
                                        savedPositionError = e.message
                                    } finally {
                                        showSavedPositionNameDialog = false
                                        pendingSavedPosition = null
                                    }
                                }
                            } else {
                                showSavedPositionNameDialog = false
                                pendingSavedPosition = null
                            }
                        },
                        onCancel = {
                            showSavedPositionNameDialog = false
                            pendingSavedPosition = null
                        }
                    )
                }
                
                if (savedPositionError != null) {
                    P(attrs = { style { color(Color("red")); fontSize(12.px); margin(0.px) } }) {
                        Text(savedPositionError!!)
                    }
                }
            }
        }

        // Title field
        DialogInput(
            label = "Title",
            value = title,
            onInput = { title = it },
            placeholder = "Enter world title",
            enabled = fieldsEnabled,
        )

        // Subtitle field
        DialogInput(
            label = "Subtitle",
            value = subtitle,
            onInput = { subtitle = it },
            placeholder = str("Enter world subtitle"),
            enabled = fieldsEnabled,
        )

        // Description field
        DialogInput(
            label = "Description",
            value = description,
            onInput = { description = it },
            placeholder = str("Enter world description"),
            enabled = fieldsEnabled,
            multiline = true,
            attrs = {
                style {
                    height(100.px)
                    property("resize", "vertical")
                }
            }
        )

        // URL field
        DialogInput(
            label = "URL",
            value = url,
            onInput = { url = it },
            placeholder = str("Enter external URL"),
            enabled = fieldsEnabled,
        )
        Label(
            attrs = {
                style {

                    fontSize(14.px)
                }
            }
        ) {
            Translated("Asset")
        }

        // File field
        DialogButton(
            onClick = {
                if (fieldsEnabled) {
                    showWorldAssetBrowser = true
                }
            },
            style = DialogButtonStyle.Primary,
            isDisabled = !fieldsEnabled
        ) {
            Translated("Browse asset")
        }
        P(attrs = {
            style {

                margin(0.px)
                fontSize(12.px)
            }
        }) {
            val name = selectedWorldAssetTitle ?: file
            if (name.isBlank()) {
                Translated("No asset selected")
            } else {
                Text(name)
            }
        }

        // Screenshot section
        Div(attrs = { classes(Styles.formGroup) }) {
            Label(attrs = { }) { Translated("World screenshot") }

            if (!screenshot.isNullOrBlank()) {
                org.jetbrains.compose.web.dom.Img(
                    src = screenshot!!,
                    attrs = {
                        classes(Styles.imageFullWidth, Styles.clickable)
                        onClick {
                            previewImageUrl = screenshot
                        }
                    }
                )
            }

            Div(attrs = { classes(Styles.requestActions) }) {
            DialogButton(
                onClick = {
                    if (!fieldsEnabled || isUploadingScreenshot) return@DialogButton
                    coroutineScope.launch {
                        isUploadingScreenshot = true
                        try {
                            val url = main.utils.ScreenshotUtils.captureAndUploadWorldScreenshot(title)
                            screenshot = url
                        } catch (t: Throwable) {
                            console.error("Failed to capture/upload screenshot", t)
                        } finally {
                            isUploadingScreenshot = false
                        }
                    }
                },
                style = DialogButtonStyle.Primary,
                isDisabled = !fieldsEnabled || isUploadingScreenshot
            ) { Translated(if (screenshot.isNullOrBlank()) "Set screenshot from scene" else if (isUploadingScreenshot) "Uploading..." else "Change screenshot") }

                if (!screenshot.isNullOrBlank()) {
                    DialogButton(
                        onClick = { if (fieldsEnabled) screenshot = null },
                        style = DialogButtonStyle.Secondary,
                        isDisabled = !fieldsEnabled
                    ) { Translated("Remove") }
                }
            }
        }

        // Music selection
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "4px")
                    property("margin-top", "16px")
                }
            }
        ) {
            Label(
                attrs = {
                    style {
                        fontSize(14.px)
                    }
                }
            ) { Translated("Music") }

            // Day/Default music controls
            Div(attrs = { classes(Styles.requestActions) }) {
                DialogButton(
                    onClick = { if (fieldsEnabled) showMusicDialog = true },
                    style = DialogButtonStyle.Primary,
                    isDisabled = !fieldsEnabled
                ) { Translated(if (musicUrl.isNullOrBlank()) "Set music" else "Change music") }

                if (!musicUrl.isNullOrBlank()) {
                    DialogButton(
                        onClick = { if (fieldsEnabled) { musicUrl = null; musicTitle = null } },
                        style = DialogButtonStyle.Secondary,
                        isDisabled = !fieldsEnabled
                    ) { Translated("Remove") }
                }
            }
                
            if (!musicUrl.isNullOrBlank()) {
                P(attrs = {
                    style {
                        margin(0.px)
                        fontSize(12.px)
                    }
                }) {
                    Text(musicTitle ?: musicUrl!!)
                }
            }

            // Night music controls (only outdoors)
            if (isOutdoors) {
                Label(
                    attrs = {
                        style { fontSize(14.px) }
                        classes(Styles.settingsSectionLabel)
                    }
                ) { Translated("Night Music") }
                Div(attrs = { classes(Styles.requestActions) }) {
                    DialogButton(
                        onClick = { if (fieldsEnabled) showNightMusicDialog = true },
                        style = DialogButtonStyle.Primary,
                        isDisabled = !fieldsEnabled
                    ) { Translated(if (nightMusicUrl.isNullOrBlank()) "Set night music" else "Change night music") }
                
                    if (!nightMusicUrl.isNullOrBlank()) {
                        DialogButton(
                            onClick = { if (fieldsEnabled) { nightMusicUrl = null; nightMusicTitle = null } },
                            style = DialogButtonStyle.Secondary,
                            isDisabled = !fieldsEnabled
                        ) { Translated("Remove") }
                    }
                }

                if (!nightMusicUrl.isNullOrBlank()) {
                    P(attrs = {
                        style {
                            margin(0.px)
                            fontSize(12.px)
                        }
                    }) {
                        Text(nightMusicTitle ?: nightMusicUrl!!)
                    }
                }
            }
        }

        if (showMusicDialog) {
            UploadMediaDialog(
                onClose = { showMusicDialog = false },
                onSelect = { url, type, name ->
                    if (type == MediaType.Audio) {
                        musicUrl = url
                        musicTitle = name
                    }
                },
                filter = MediaType.Audio,
            )
        }

        if (showNightMusicDialog) {
            UploadMediaDialog(
                onClose = { showNightMusicDialog = false },
                onSelect = { url, type, name ->
                    if (type == MediaType.Audio) {
                        nightMusicUrl = url
                        nightMusicTitle = name
                    }
                },
                filter = MediaType.Audio,
            )
        }

        val previewUrl = previewImageUrl
        if (previewUrl != null) {
            ImageDialog(
                imageUrl = previewUrl,
                onClose = { previewImageUrl = null },
            )
        }

        // Asset browser dialog for selecting world file
        if (showWorldAssetBrowser) {
            AssetBrowserDialog(
                type = AssetType.World,
                onSelect = { result ->
                    file = result.asset!!.file
                    assetId = result.asset!!.key
                    selectedWorldAssetTitle = result.title ?: result.asset?.file
                    showWorldAssetBrowser = false

                    // Live preview: reload the scene with the newly selected world file
                    try {
                        val lat = latitude.toDoubleOrNull()
                        val lon = longitude.toDoubleOrNull()
                        val latLngList =
                            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(
                                lat,
                                lon
                            ) else null

                        val previewWorld = if (isEditing) {
                            currentWorld.copy(
                                file = file,
                                assetId = assetId,
                                isOutdoors = isOutdoors,
                                latLng = latLngList,
                                backgroundColor = backgroundColor,
                                backgroundColorIntensity = backgroundColorIntensity,
                                directionalLightColor = directionalLightColor,
                                directionalLightIntensity = directionalLightIntensity,
                                fogDensity = fogDensity,
                                musicUrl = musicUrl,
                                musicTitle = musicTitle,
                                nightMusicUrl = nightMusicUrl,
                                nightMusicTitle = nightMusicTitle,
                            )
                        } else {
                            // Create temporary world model for preview when creating a new world
                            World(
                                title = title,
                                subtitle = subtitle,
                                description = description,
                                url = url,
                                assetId = assetId,
                                file = file,
                                isOutdoors = isOutdoors,
                                latLng = latLngList,
                                backgroundColor = backgroundColor,
                                backgroundColorIntensity = backgroundColorIntensity,
                                directionalLightColor = directionalLightColor,
                                directionalLightIntensity = directionalLightIntensity,
                                fogDensity = fogDensity,
                                // Access control (use current form values)
                                allowAllVisitors = allowAllVisitors,
                                allowAllPlayers = allowAllPlayers,
                                allowFriendsOfOwners = allowFriendsOfOwners,
                                allowHoldersOfItemId = allowHoldersOfItemId?.takeIf { it.isNotBlank() },
                                chatLongevity = 0.0,
                            )
                        }

                        WorldLoader.reloadWorldFile(previewWorld)
                    } catch (e: Throwable) {
                        console.warn("[WARN] Failed to preview world file: ${e.message}")
                    }
                },
                onClose = { showWorldAssetBrowser = false },
            )
        }

        // Outdoors (realtime sun)
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "4px")
                    property("margin-top", "16px")
                }
            }
        ) {
            Label(
                attrs = {
                    style {

                        fontSize(14.px)
                    }
                }
            ) {
                Translated("Outdoors")
            }

            LabeledCheckbox(
                checked = isOutdoors,
                enabled = fieldsEnabled,
                label = translated("Use real-time sun lighting based on latitude and longitude"),
                onCheckedChange = { next ->
                    isOutdoors = next
                    previewLightingFromForm()
                },
            )
        }

        // Latitude / Longitude fields
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "4px")
                }
            }
        ) {
            Label(
                attrs = {
                    style {

                        fontSize(14.px)
                    }
                }
            ) { Translated("Latitude and longitude") }

            // Mini globe picker (click to select coordinates)
            LatLngPicker3D(
                lat = latitude.toDoubleOrNull(),
                lng = longitude.toDoubleOrNull(),
                enabled = fieldsEnabled,
                onChange = { la, lo ->
                    latitude = la.toString()
                    longitude = lo.toString()
                    previewLightingFromForm()
                },
            )

            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                    }
                }
            ) {
                DecimalNumberInput(
                    value = latitude,
                    enabled = fieldsEnabled,
                    placeholder = "Latitude (-90 to 90)",
                    onChange = { next ->
                        latitude = next
                        previewLightingFromForm()
                    },
                )

                DecimalNumberInput(
                    value = longitude,
                    enabled = fieldsEnabled,
                    placeholder = "Longitude (-180 to 180)",
                    onChange = { next ->
                        longitude = next
                        previewLightingFromForm()
                    },
                )
            }
        }

        // Access control
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "4px")
                    property("margin-top", "16px")
                }
            }
        ) {
            Label(
                attrs = {
                    style {

                        fontSize(14.px)
                    }
                }
            ) { Translated("Access control") }

            // All visitors
            LabeledCheckbox(
                checked = allowAllVisitors,
                enabled = fieldsEnabled,
                label = translated("All visitors"),
                onCheckedChange = { next ->
                    allowAllVisitors = next
                    if (next) {
                        if (!allowAllPlayers) allowAllPlayers = true
                        if (!allowFriendsOfOwners) allowFriendsOfOwners = true
                        if (allowHoldersOfItemId == null) allowHoldersOfItemId = ""
                    }
                },
            )

            // All players
            LabeledCheckbox(
                checked = allowAllPlayers,
                enabled = fieldsEnabled && !allowAllVisitors,
                label = translated("All players"),
                onCheckedChange = { next ->
                    allowAllPlayers = next
                    if (next && !allowAllVisitors) {
                        if (!allowFriendsOfOwners) allowFriendsOfOwners = true
                        if (allowHoldersOfItemId == null) allowHoldersOfItemId = ""
                    }
                },
            )

            // Friends of owners
            LabeledCheckbox(
                checked = allowFriendsOfOwners,
                enabled = fieldsEnabled && !allowAllVisitors && !allowAllPlayers,
                label = translated("Friends of owners"),
                onCheckedChange = { next ->
                    allowFriendsOfOwners = next
                },
            )

            // Holders of an item (access card)
            LabeledCheckbox(
                checked = allowHoldersOfItemId != null,
                enabled = fieldsEnabled && !allowAllVisitors && !allowAllPlayers,
                label = translated("Holders of an item (access card)"),
                onCheckedChange = { next ->
                    allowHoldersOfItemId = if (next) "" else null
                    accessItemError = null
                },
            )

            if (allowHoldersOfItemId != null) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                    }
                }) {
                    DialogButton(
                        onClick = { showAccessItemBrowser = true },
                        style = DialogButtonStyle.Primary,
                        isDisabled = !fieldsEnabled || allowAllVisitors || allowAllPlayers
                    ) { Translated("Select item") }

                    P(attrs = {
                        style {
                            margin(0.px); fontSize(12.px)
                        }
                    }) {
                        if (allowHoldersOfItemId!!.isBlank()) {
                            Translated("No item")
                        } else {
                            var item by remember { mutableStateOf(null as Item?) }

                            LaunchedEffect(allowHoldersOfItemId) {
                                allowHoldersOfItemId?.takeIf { it.isNotBlank() }?.let { itemId ->
                                    item = ItemApi.getItemById(itemId)
                                }
                            }
                            item?.let { item ->
                                Translated(item.title)
                            }
                        }
                    }

                    if (allowHoldersOfItemId!!.isNotBlank()) {
                        DialogButton(
                            onClick = { allowHoldersOfItemId = null },
                            style = DialogButtonStyle.Secondary,
                            isDisabled = !fieldsEnabled || allowAllVisitors || allowAllPlayers
                        ) { Translated("Clear") }
                    }
                }

                if (accessItemError != null) {
                    P(attrs = {
                        style {
                            color(Color.red); margin(0.px)
                            fontSize(12.px)
                        }
                    }) { Text(accessItemError!!) }
                }

                if (showAccessItemBrowser) {
                    SelectItemDialog(
                        initial = emptyList(),
                        multiple = false,
                        useQuantities = false,
                        onConfirm = { selected ->
                            accessItemError = null
                            val first = selected.firstOrNull()?.first
                            if (first != null) {
                                allowHoldersOfItemId = first.key
                                showAccessItemBrowser = false
                            } else {
                                accessItemError = "Please select an item"
                            }
                        },
                        onClose = { showAccessItemBrowser = false },
                    )
                }
            }
        }

        // Chat history
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "4px")
                    property("margin-top", "16px")
                }
            }
        ) {
            Label(
                attrs = {
                    style {

                        fontSize(14.px)
                    }
                }
            ) { Translated("Chat history") }

            // Enable expiration
            LabeledCheckbox(
                checked = chatLongevityEnabled,
                enabled = fieldsEnabled,
                label = translated("Expire messages"),
                onCheckedChange = { next -> chatLongevityEnabled = next },
            )

            if (chatLongevityEnabled) {
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            property("gap", "8px")
                            property("align-items", "center")
                        }
                    }
                ) {
                    DecimalNumberInput(
                        value = chatLongevityDays,
                        enabled = fieldsEnabled,
                        placeholder = translated("Keep messages for (days)"),
                        onChange = { next -> chatLongevityDays = next },
                    )
                }
            }
        }

        // Background Color field
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "4px")
                }
            }
        ) {
            Label(
                attrs = {
                    style {

                        fontSize(14.px)
                    }
                }
            ) {
                Translated("Background color")
            }

            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                    }
                }
            ) {
                Input(
                    type = InputType.Color,
                    attrs = {
                        style {
                            property("width", "50px")
                            property("height", "30px")
                            property("padding", "0")
                            property("border", "none")
                            if (!fieldsEnabled) {
                                property("opacity", "0.7")
                                property("cursor", "not-allowed")
                            }
                        }
                        value(backgroundColor)
                        if (fieldsEnabled) {
                            onInput { event ->
                                backgroundColor = event.value
                                val lat = latitude.toDoubleOrNull()
                                val lon = longitude.toDoubleOrNull()
                                val outdoorsRealtime =
                                    isOutdoors && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
                                if (!outdoorsRealtime) {
                                    // Update the scene background color in real-time
                                    updateSceneBackgroundColor(event.value)
                                } else {
                                    console.log("[INFO] Skipping background color apply from editor (realtime outdoors active)")
                                }
                            }
                        }
                        if (!fieldsEnabled) {
                            attr("disabled", "true")
                        }
                    }
                )

                // Display the hex color value next to the color picker
                Div(
                    attrs = {
                        style {

                            fontSize(14.px)
                        }
                    }
                ) {
                    Text(backgroundColor)
                }
            }

            // Background color intensity slider
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                        property("margin-top", "4px")
                    }
                }
            ) {
                var showIntensityPrompt by remember { mutableStateOf(false) }
                RangeInput(
                    value = backgroundColorIntensity,
                    min = 0.0,
                    max = 10.0,
                    step = 0.1,
                    attrs = {
                        style {
                            property("width", "200px")
                            if (!fieldsEnabled) {
                                property("opacity", "0.7")
                                property("cursor", "not-allowed")
                            }
                        }
                        if (!fieldsEnabled) {
                            disabled()
                        }
                        onInput { event ->
                            backgroundColorIntensity = event.value?.toFloat() ?: 1.0f
                            val lat = latitude.toDoubleOrNull()
                            val lon = longitude.toDoubleOrNull()
                            val outdoorsRealtime =
                                isOutdoors && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
                            if (!outdoorsRealtime) {
                                WorldLoader.updateHemisphericLightIntensity(backgroundColorIntensity)
                            }
                        }
                    }
                )
                Div(
                    attrs = {
                        style {
                            property("min-width", "40px")
                            if (fieldsEnabled) {
                                cursor("pointer")
                            } else {
                                property("opacity", "0.7")
                                cursor("not-allowed")
                            }
                        }
                        if (fieldsEnabled) {
                            attr("title", str("Click to edit"))
                            onClick { showIntensityPrompt = true }
                        }
                    }
                ) {
                    Text(backgroundColorIntensity.toString())
                }
                if (showIntensityPrompt) {
                    BasicPromptDialog(
                        title = translated("Intensity"),
                        defaultValue = backgroundColorIntensity.toString(),
                        isNumber = true,
                        min = 0f,
                        max = 10f,
                        step = 0.1f,
                        onSubmit = { input ->
                            val parsed = input.toFloatOrNull()
                            if (parsed != null) {
                                backgroundColorIntensity = parsed.coerceAtLeast(0f)
                                WorldLoader.updateHemisphericLightIntensity(backgroundColorIntensity)
                            }
                            showIntensityPrompt = false
                        },
                        onCancel = { showIntensityPrompt = false },
                    )
                }
            }
        }

        // Directional Light Color field
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "4px")
                    property("margin-top", "16px")
                }
            }
        ) {
            Label(
                attrs = {
                    style {

                        fontSize(14.px)
                    }
                }
            ) {
                Translated("Light color")
            }

            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                    }
                }
            ) {
                Input(
                    type = InputType.Color,
                    attrs = {
                        style {
                            property("width", "50px")
                            property("height", "30px")
                            property("padding", "0")
                            property("border", "none")
                            if (!fieldsEnabled) {
                                property("opacity", "0.7")
                                property("cursor", "not-allowed")
                            }
                        }
                        value(directionalLightColor)
                        if (fieldsEnabled) {
                            onInput { event ->
                                directionalLightColor = event.value
                                // Update the directional light color in real-time
                                WorldLoader.updateDirectionalLightColorHex(event.value)
                            }
                        }
                        if (!fieldsEnabled) {
                            attr("disabled", "true")
                        }
                    }
                )

                // Display the hex color value next to the color picker
                Div(
                    attrs = {
                        style {

                            fontSize(14.px)
                        }
                    }
                ) {
                    Text(directionalLightColor)
                }
            }

            // Directional light intensity slider
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                        property("margin-top", "4px")
                    }
                }
            ) {
                var showIntensityPrompt by remember { mutableStateOf(false) }
                RangeInput(
                    value = directionalLightIntensity,
                    min = 0.0,
                    max = 10.0,
                    step = 0.1,
                    attrs = {
                        style {
                            property("width", "200px")
                            if (!fieldsEnabled) {
                                property("opacity", "0.7")
                                property("cursor", "not-allowed")
                            }
                        }
                        if (!fieldsEnabled) {
                            disabled()
                        }
                        onInput { event ->
                            directionalLightIntensity = event.value?.toFloat() ?: 1.0f
                            val lat = latitude.toDoubleOrNull()
                            val lon = longitude.toDoubleOrNull()
                            val outdoorsRealtime =
                                isOutdoors && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
                            if (!outdoorsRealtime) {
                                WorldLoader.updateDirectionalLightIntensity(directionalLightIntensity)
                            }
                        }
                    }
                )
                Div(
                    attrs = {
                        style {
                            property("min-width", "40px")
                            if (fieldsEnabled) {
                                cursor("pointer")
                            } else {
                                property("opacity", "0.7")
                                cursor("not-allowed")
                            }
                        }
                        if (fieldsEnabled) {
                            attr("title", str("Click to edit"))
                            onClick { showIntensityPrompt = true }
                        }
                    }
                ) {
                    Text(directionalLightIntensity.toString())
                }
                if (showIntensityPrompt) {
                    BasicPromptDialog(
                        title = translated("Intensity"),
                        defaultValue = directionalLightIntensity.toString(),
                        isNumber = true,
                        min = 0f,
                        max = 10f,
                        step = 0.1f,
                        onSubmit = { input ->
                            val parsed = input.toFloatOrNull()
                            if (parsed != null) {
                                directionalLightIntensity = parsed.coerceAtLeast(0f)
                                WorldLoader.updateDirectionalLightIntensity(directionalLightIntensity)
                            }
                            showIntensityPrompt = false
                        },
                        onCancel = { showIntensityPrompt = false },
                    )
                }
            }
        }

        // Fog Density field
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "4px")
                    property("margin-top", "16px")
                }
            }
        ) {
            Label(
                attrs = {
                    style {

                        fontSize(14.px)
                    }
                }
            ) {
                Translated("Fog density")
            }

            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                    }
                }
            ) {
                var showFogPrompt by remember { mutableStateOf(false) }
                // Slider for fog density
                RangeInput(
                    value = fogDensity,
                    min = 0.0,
                    max = 1.0,
                    step = 0.001,
                    attrs = {
                        style {
                            property("width", "200px")
                            if (!fieldsEnabled) {
                                property("opacity", "0.7")
                                property("cursor", "not-allowed")
                            }
                        }

                        // Set disabled state
                        if (!fieldsEnabled) {
                            disabled()
                        }

                        // Handle input event
                        onInput { event ->
                            // Update the state
                            fogDensity = event.value?.toFloat() ?: 0.0f
                            // Update the scene fog density in real-time
                            updateSceneFogDensity(fogDensity)
                        }
                    }
                )

                // Display the fog density value next to the slider
                Div(
                    attrs = {
                        style {
                            property("min-width", "40px")
                            if (fieldsEnabled) {
                                cursor("pointer")
                            } else {
                                property("opacity", "0.7")
                                cursor("not-allowed")
                            }
                        }
                        if (fieldsEnabled) {
                            attr("title", str("Click to edit"))
                            onClick { showFogPrompt = true }
                        }
                    }
                ) {
                    Text(fogDensity.toString())
                }

                if (showFogPrompt) {
                    BasicPromptDialog(
                        title = translated("Fog density"),
                        defaultValue = fogDensity.toString(),
                        isNumber = true,
                        min = 0f,
                        max = 1f,
                        step = 0.001f,
                        onSubmit = { input ->
                            val parsed = input.toFloatOrNull()
                            if (parsed != null) {
                                fogDensity = parsed.coerceIn(0f, 1f)
                                updateSceneFogDensity(fogDensity)
                            }
                            showFogPrompt = false
                        },
                        onCancel = { showFogPrompt = false },
                    )
                }
            }
        }

        // Delete world (owner only)
        if (isEditing && isOwner) {
            var showDeleteConfirm by remember { mutableStateOf(false) }
            var isDeleting by remember { mutableStateOf(false) }
            var deleteError by remember { mutableStateOf<String?>(null) }

            Div(
                attrs = {
                    style {
                        property("margin-top", "24px")
                    }
                }
            ) {
                DialogButton(
                    onClick = { showDeleteConfirm = true },
                    style = DialogButtonStyle.Danger,
                    attrs = {
                        style {
                            property("width", "100%")
                        }
                    }
                ) { Translated("Delete world") }
            }

            if (showDeleteConfirm) {
                BaseDialog(
                    onClose = { if (!isDeleting) showDeleteConfirm = false },
                    title = translated("Delete world"),
                    clickOutside = !isDeleting,
                    content = {
                        P { Translated("Are you sure you want to delete this world? This cannot be undone.") }
                        if (deleteError != null) {
                            P(attrs = { style { color(Color.red) } }) { Text(deleteError!!) }
                        }
                    },
                    actions = {
                        DialogButton(
                            onClick = { if (!isDeleting) showDeleteConfirm = false },
                            style = DialogButtonStyle.Secondary,
                            isDisabled = isDeleting,
                        ) { Translated("Cancel") }

                        DialogButton(
                            onClick = {
                                if (isDeleting) return@DialogButton
                                isDeleting = true
                                deleteError = null
                                coroutineScope.launch {
                                    try {
                                        WorldApi.deleteWorld(currentWorld.key)
                                        isDeleting = false
                                        showDeleteConfirm = false
                                        didSave = true
                                        router.navigate("/")
                                        onClose()
                                    } catch (t: Throwable) {
                                        isDeleting = false
                                        deleteError = t.message ?: str("An error occurred")
                                    }
                                }
                            },
                            style = DialogButtonStyle.Danger,
                            isDisabled = isDeleting,
                        ) { Translated("Delete") }
                    }
                )
            }
        }
    }

    // Price and balance info above action buttons (create flow)
    run {
        if (isCreating) {
            val price = worldBasePrice
            if (price != null) {
                Div(
                    attrs = {
                        style {
                            padding(12.px)
                            backgroundColor(rgba(255, 255, 255, 0.03))
                            property("border-top", "1px solid rgba(255, 255, 255, 0.12)")
                            property("border-bottom", "1px solid rgba(255, 255, 255, 0.12)")
                        }
                    }
                ) {
                    // World price
                    P {
                        Label { Translated("World price") }
                        Text(": ${formatAmount(price)} ${translated("VND")}")
                    }
                    // Balance
                    val bal = myBalance
                    if (bal != null) {
                        P {
                            Label { Translated("Your balance") }
                            Text(": ${formatAmount(bal)} ${translated("VND")}")
                        }
                    }
                    // Insufficient funds warning
                    val insufficient = bal != null && bal < price
                    if (insufficient) {
                        P(attrs = { classes(Styles.formErrorMessage) }) { Translated("Insufficient funds") }
                    }
                }
            }
        }
    }

    // Footer with action buttons
    Div(
        attrs = {
            style {
                padding(16.px)
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                property("gap", "8px")
                property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
            }
        }
    ) {
        // Cancel button
        DialogButton(
            onClick = {
                restoreOriginalState()
                onClose()
            },
            style = DialogButtonStyle.Secondary,
            attrs = {
                style {
                    flexGrow(1)
                }
            }
        ) {
            Translated("Cancel")
        }

        // Save button
        DialogButton(
            onClick = {
                if (!isSubmitting && fieldsEnabled) {
                    if (isCreating) {
                        showConfirmPurchase = true
                    } else {
                        handleSubmit()
                    }
                }
            },
            style = DialogButtonStyle.Primary,
            isDisabled = run {
                val price = worldBasePrice
                val bal = myBalance
                val insufficient = isCreating && price != null && bal != null && bal < price
                !fieldsEnabled || isSubmitting || insufficient
            },
            attrs = {
                style {
                    flexGrow(1)
                }
            }
        ) {
            Translated(
                when {
                    isSubmitting -> "Saving..."
                    isEditing && isOwner -> "Update"
                    isSubmitting -> "Creating..."
                    else -> "Create"
                }
            )
        }
    }

    // Confirm purchase dialog
    if (showConfirmPurchase) {
        val price = worldBasePrice ?: 0L
        BasicConfirmDialog(
            message = "${translated("You are about to purchase a world for")} ${formatAmount(price)} ${translated("VND")}. ${
                translated(
                    "Continue?"
                )
            }",
            onConfirm = {
                showConfirmPurchase = false
                handleSubmit()
            },
            onCancel = { showConfirmPurchase = false },
            title = translated("Confirm purchase"),
        )
    }
}
