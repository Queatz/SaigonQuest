package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import main.theme.Styles
import models.User
import models.World
import models.AssetType
import main.api.ItemApi
import main.api.AssetItemApi
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
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.RangeInput
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import app.softwork.routingcompose.Router
import main.components.world.LatLngPicker3D
import org.jetbrains.compose.web.dom.CheckboxInput

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
    onSave: (World) -> Unit
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
                val color3 = Color3.FromHexString(hexColor).toLinearSpace()
                
                // Convert Color3 to Color4 with alpha=1 (fully opaque)
                val color4 = color3.toColor4()
                color4.a = 1.0f
                
                // Set the scene's clear color
                scene.clearColor = color4

                // Sync hemispheric light color to match background
                WorldLoader.updateHemisphericLightColorHex(hexColor)
                
                // If fog is enabled (fogDensity > 0), update the fog color to match the background color
                if (scene.fogDensity > 0.0f) {
                    scene.fogColor = color3
                    println("[DEBUG] Updated scene background color to $hexColor and synced fog color")
                } else {
                    println("[DEBUG] Updated scene background color to $hexColor")
                }
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
                    scene.fogMode = Scene.Companion.FOGMODE_EXP2
                    
                    // Scale the density for better visual effect (0.0-1.0 to 0.0-0.1)
                    scene.fogDensity = density * 0.1f
                    
                    // Set fog color to match background color
                    val clearColor = scene.clearColor
                    scene.fogColor = Color3(clearColor.r, clearColor.g, clearColor.b)
                    
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
    var directionalLightColor by remember { mutableStateOf(currentWorld?.directionalLightColor ?: "#ffffff") }
    var fogDensity by remember { mutableStateOf(currentWorld?.fogDensity ?: 0.0f) }
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
        directionalLightColor = currentWorld?.directionalLightColor ?: "#ffffff"
        fogDensity = currentWorld?.fogDensity ?: 0.0f
        isOutdoors = currentWorld?.isOutdoors ?: false
        latitude = currentWorld?.latLng?.getOrNull(0)?.toString() ?: ""
        longitude = currentWorld?.latLng?.getOrNull(1)?.toString() ?: ""
        allowAllVisitors = currentWorld?.allowAllVisitors ?: true
        allowAllPlayers = currentWorld?.allowAllPlayers ?: false
        allowFriendsOfOwners = currentWorld?.allowFriendsOfOwners ?: false
        allowHoldersOfItemId = currentWorld?.allowHoldersOfItemId

        try {
            // Update scene preview to reflect new world values
            val lat = latitude.toDoubleOrNull()
            val lon = longitude.toDoubleOrNull()
            val outdoorsRealtime = isOutdoors && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
            if (!outdoorsRealtime) {
                updateSceneBackgroundColor(backgroundColor)
            }
            updateSceneFogDensity(fogDensity)
            WorldLoader.updateDirectionalLightColorHex(directionalLightColor)
        } catch (_: Throwable) {}
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

    // Determine if we're editing or creating
    val isEditing = currentWorld != null
    
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
    
    // Apply initial background color, fog, and light settings when component is mounted
    LaunchedEffect(Unit) {
        // Determine if realtime outdoors mode should control background
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        val outdoorsRealtime = isOutdoors && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0

        // Apply the initial background color only if not outdoors realtime
        if (!outdoorsRealtime) {
            updateSceneBackgroundColor(backgroundColor)
        } else {
            console.log("[INFO] Skipping background color apply because realtime outdoors mode is active")
        }
        
        // Apply the initial fog density
        updateSceneFogDensity(fogDensity)
        
        // Apply the initial directional light color
        WorldLoader.updateDirectionalLightColorHex(directionalLightColor)
    }
    
    // Determine if the current user is the owner of the world
    val isOwner = if (isEditing && currentUser != null) {
        currentWorld?.userId == currentUser?.key
    } else {
        // If creating a new world or user not loaded yet, default to true
        true
    }
    
    // Determine if form fields should be enabled
    val fieldsEnabled = !isEditing || isOwner
    
    // Snapshot the active world at editor open to allow restore on cancel
    val initialActiveWorldSnapshot = remember { WorldLoader.getActiveWorld() }
    // Track whether a save occurred
    var didSave by remember { mutableStateOf(false) }

    // Function to restore original lighting/state
    fun restoreOriginalState() {
        try {
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
    val handleSubmit = {
        isSubmitting = true
        errorMessage = ""
        
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
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(lat, lon) else null
                },
                backgroundColor = backgroundColor,
                directionalLightColor = directionalLightColor,
                fogDensity = fogDensity,
                allowAllVisitors = allowAllVisitors,
                allowAllPlayers = allowAllPlayers,
                allowFriendsOfOwners = allowFriendsOfOwners,
                allowHoldersOfItemId = allowHoldersOfItemId?.takeIf { it.isNotBlank() }
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
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(lat, lon) else null
                },
                backgroundColor = backgroundColor,
                directionalLightColor = directionalLightColor,
                fogDensity = fogDensity,
                allowAllVisitors = allowAllVisitors,
                allowAllPlayers = allowAllPlayers,
                allowFriendsOfOwners = allowFriendsOfOwners,
                allowHoldersOfItemId = allowHoldersOfItemId?.takeIf { it.isNotBlank() }
            )
        }
        
        coroutineScope.launch {
            try {
                val fileChanged = isEditing && currentWorld!!.file != worldModel.file
                val savedWorld = if (isEditing) {
                    WorldApi.updateWorld(currentWorld!!.key, worldModel)
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
            val latLngList = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(lat, lon) else null
            val previewWorld = World(
                // Only fields relevant to lighting are necessary
                isOutdoors = isOutdoors,
                latLng = latLngList,
                backgroundColor = backgroundColor,
                directionalLightColor = directionalLightColor,
                fogDensity = fogDensity,
            )
            WorldLoader.notifyWorldEdited(previewWorld)
        } catch (e: Throwable) {
            console.warn("[WARN] Failed to preview lighting from form: ${e.message}")
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
        // Error message
        if (errorMessage.isNotEmpty()) {
            P(
                attrs = {
                    style {
                        color(Color.red)
                        margin(0.px)
                    }
                }
            ) {
                Text(errorMessage)
            }
        }
        
        // Ownership message
        if (isEditing && !isOwner) {
            P(
                attrs = {
                    style {
                        color(Color.white)
                        backgroundColor(rgba(0, 0, 0, 0.3))
                        padding(8.px)
                        property("border-radius", "4px")
                        margin(0.px)
                    }
                }
            ) {
                Text("You don't own this world.")
            }
        }
        
        // Title field
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) {
                Text("Title")
            }
            
            Input(
                type = InputType.Text,
                attrs = {
                    classes(Styles.inputField)
                    style {
                        if (!fieldsEnabled) {
                            property("opacity", "0.7")
                            property("cursor", "not-allowed")
                        }
                    }
                    value(title)
                    if (fieldsEnabled) {
                        onInput { event -> title = event.value }
                    }
                    placeholder("Enter world title")
                    if (!fieldsEnabled) {
                        attr("disabled", "true")
                    }
                }
            )
        }
        
        // Subtitle field
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) {
                Text("Subtitle")
            }
            
            Input(
                type = InputType.Text,
                attrs = {
                    classes(Styles.inputField)
                    style {
                        if (!fieldsEnabled) {
                            property("opacity", "0.7")
                            property("cursor", "not-allowed")
                        }
                    }
                    value(subtitle)
                    if (fieldsEnabled) {
                        onInput { event -> subtitle = event.value }
                    }
                    placeholder("Enter world subtitle")
                    if (!fieldsEnabled) {
                        attr("disabled", "true")
                    }
                }
            )
        }
        
        // Description field
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) {
                Text("Description")
            }
            
            TextArea(
                attrs = {
                    classes(Styles.inputField)
                    style {
                        height(100.px)
                        property("resize", "vertical")
                        if (!fieldsEnabled) {
                            property("opacity", "0.7")
                            property("cursor", "not-allowed")
                        }
                    }
                    value(description)
                    if (fieldsEnabled) {
                        onInput { event -> description = event.value }
                    }
                    placeholder("Enter world description")
                    if (!fieldsEnabled) {
                        attr("disabled", "true")
                    }
                }
            )
        }
        
        // URL field
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) {
                Text("URL")
            }
            
            Input(
                type = InputType.Text,
                attrs = {
                    classes(Styles.inputField)
                    style {
                        if (!fieldsEnabled) {
                            property("opacity", "0.7")
                            property("cursor", "not-allowed")
                        }
                    }
                    value(url)
                    if (fieldsEnabled) {
                        onInput { event -> url = event.value }
                    }
                    placeholder("Enter external URL")
                    if (!fieldsEnabled) {
                        attr("disabled", "true")
                    }
                }
            )
        }
        Label(
            attrs = {
                style {
                    color(Color.white)
                    fontSize(14.px)
                }
            }
        ) {
            Text("Asset")
        }
        
        // File field
        Button(attrs = {
            onClick {
                if (fieldsEnabled) {
                    showWorldAssetBrowser = true
                }
            }
            style {
                padding(8.px, 12.px)
                backgroundColor(rgba(0, 120, 255, 0.7))
                color(Color.white)
                property("border", "0")
                property("border-radius", "4px")
                cursor(if (fieldsEnabled) "pointer" else "not-allowed")
            }
        }) {
            Translated("Browse asset")
        }
        P(attrs = {
            style {
                color(Color.white)
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

        // Asset browser dialog for selecting world file
        if (showWorldAssetBrowser) {
            AssetBrowserDialog(
                type = AssetType.World,
                onSelect = { result ->
                    file = result.asset!!.file
                    assetId = result.asset!!.key
                    selectedWorldAssetTitle = result.title ?: result.asset?.file
                    showWorldAssetBrowser = false
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) {
                Translated("Outdoors")
            }

            // Checkbox control
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                    }
                }
            ) {
                CheckboxInput(
                    checked = isOutdoors,
                    attrs = {
                        if (!fieldsEnabled) {
                            disabled()
                        }
                        onChange {
                            isOutdoors = !isOutdoors
                            previewLightingFromForm()
                        }
                    }
                )

                // Hint text
                P(
                    attrs = {
                        style {
                            color(Color.white)
                            margin(0.px)
                            fontSize(12.px)
                        }
                    }
                ) { Text("Use real-time sun lighting based on latitude and longitude") }
            }
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) { Text("Latitude and longitude") }

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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) { Translated("Access control") }

            // All visitors
            Div(attrs = { style { display(DisplayStyle.Flex); property("gap", "8px"); property("align-items", "center") } }) {
                CheckboxInput(
                    checked = allowAllVisitors,
                    attrs = {
                        if (!fieldsEnabled) disabled()
                        onChange {
                            val newVal = !allowAllVisitors
                            allowAllVisitors = newVal
                            if (newVal) {
                                if (!allowAllPlayers) allowAllPlayers = true
                                if (!allowFriendsOfOwners) allowFriendsOfOwners = true
                                if (allowHoldersOfItemId == null) allowHoldersOfItemId = ""
                            }
                        }
                    }
                )
                P(attrs = { style { color(Color.white); margin(0.px); fontSize(12.px) } }) { Translated("All visitors") }
            }

            // All players
            Div(attrs = { style { display(DisplayStyle.Flex); property("gap", "8px"); property("align-items", "center") } }) {
                CheckboxInput(
                    checked = allowAllPlayers,
                    attrs = {
                        if (!fieldsEnabled || allowAllVisitors) disabled()
                        onChange {
                            val newVal = !allowAllPlayers
                            allowAllPlayers = newVal
                            if (newVal && !allowAllVisitors) {
                                if (!allowFriendsOfOwners) allowFriendsOfOwners = true
                                if (allowHoldersOfItemId == null) allowHoldersOfItemId = ""
                            }
                        }
                    }
                )
                P(attrs = { style { color(Color.white); margin(0.px); fontSize(12.px) } }) { Translated("All players") }
            }

            // Friends of owners
            Div(attrs = { style { display(DisplayStyle.Flex); property("gap", "8px"); property("align-items", "center") } }) {
                CheckboxInput(
                    checked = allowFriendsOfOwners,
                    attrs = {
                        if (!fieldsEnabled || allowAllVisitors || allowAllPlayers) disabled()
                        onChange { allowFriendsOfOwners = !allowFriendsOfOwners }
                    }
                )
                P(attrs = { style { color(Color.white); margin(0.px); fontSize(12.px) } }) { Translated("Friends of owners") }
            }

            // Holders of an item (access card)
            Div(attrs = { style { display(DisplayStyle.Flex); property("gap", "8px"); property("align-items", "center") } }) {
                CheckboxInput(
                    checked = allowHoldersOfItemId != null,
                    attrs = {
                        if (!fieldsEnabled || allowAllVisitors || allowAllPlayers) disabled()
                        onChange {
                            allowHoldersOfItemId = if (allowHoldersOfItemId == null) "" else null
                            accessItemError = null
                        }
                    }
                )
                P(attrs = { style { color(Color.white); margin(0.px); fontSize(12.px) } }) { Translated("Holders of an item (access card)") }
            }

            if (!allowHoldersOfItemId.isNullOrBlank()) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("align-items", "center")
                    }
                }) {
                    Button(attrs = {
                        classes(Styles.baseDialogButtonPrimary)
                        if (!fieldsEnabled || allowAllVisitors || allowAllPlayers) disabled()
                        onClick { showAccessItemBrowser = true }
                    }) { Translated("Select item") }

                    P(attrs = {
                        style {
                            color(Color.white)
                            margin(0.px); fontSize(12.px)
                        }
                    }) {
                        if (allowHoldersOfItemId!!.isBlank()) {
                            Translated("No item selected")
                        } else {
                            Text("${translated("Selected item")}: $allowHoldersOfItemId")
                        }
                    }

                    Button(attrs = {
                        classes(Styles.baseDialogButtonSecondary)
                        if (!fieldsEnabled || allowAllVisitors || allowAllPlayers) disabled()
                        onClick { allowHoldersOfItemId = null }
                    }) { Translated("Clear") }
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
                    AssetBrowserDialog(
                        type = AssetType.Items,
                        onSelect = { r ->
                            accessItemError = null
                            coroutineScope.launch {
                                try {
                                    val items = ItemApi.getAllItems()
                                    val idx = r.index ?: 0
                                    val matched = items.find { it.file == r.asset!!.file && it.assetIndex == idx }
                                    if (matched != null) {
                                        allowHoldersOfItemId = matched.key
                                        showAccessItemBrowser = false
                                    } else {
                                        accessItemError = "No item found for this asset selection. Please create an item for this asset first."
                                    }
                                } catch (t: Throwable) {
                                    accessItemError = t.message ?: "Failed to load items"
                                }
                            }
                        },
                        onClose = { showAccessItemBrowser = false },
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) {
                Text("Background Color")
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
                                val outdoorsRealtime = isOutdoors && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
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
                            color(Color.white)
                            fontSize(14.px)
                        }
                    }
                ) {
                    Text(backgroundColor)
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) {
                Text("Light color")
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
                            color(Color.white)
                            fontSize(14.px)
                        }
                    }
                ) {
                    Text(directionalLightColor)
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
                        color(Color.white)
                        fontSize(14.px)
                    }
                }
            ) {
                Text("Fog Density")
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
                            color(Color.white)
                            fontSize(14.px)
                            property("min-width", "40px")
                        }
                    }
                ) {
                    Text(fogDensity.toString())
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
        Button(
            attrs = {
                style {
                    padding(8.px, 16.px)
                    backgroundColor(rgba(255, 255, 255, 0.1))
                    color(Color.white)
                    property("border", "1px solid rgba(255, 255, 255, 0.2)")
                    property("border-radius", "4px")
                    cursor("pointer")
                    flexGrow(1)
                }
                onClick {
                    restoreOriginalState()
                    onClose()
                }
            }
        ) {
            Text("Cancel")
        }
        
        // Save button
        Button(
            attrs = {
                style {
                    padding(8.px, 16.px)
                    backgroundColor(
                        when {
                            isSubmitting -> rgba(100, 100, 100, 0.5)
                            !fieldsEnabled -> rgba(100, 100, 100, 0.5)
                            else -> rgba(0, 120, 255, 0.7)
                        }
                    )
                    color(Color.white)
                    property("border", "0")
                    property("border-radius", "4px")
                    cursor(
                        when {
                            isSubmitting -> "default"
                            !fieldsEnabled -> "not-allowed"
                            else -> "pointer"
                        }
                    )
                    flexGrow(1)
                }
                // Only allow clicking if not submitting and fields are enabled
                onClick { if (!isSubmitting && fieldsEnabled) handleSubmit() }
                // Disable button if fields are disabled
                if (!fieldsEnabled) {
                    attr("disabled", "true")
                }
            }
        ) {
            Text(
                when {
                    isSubmitting -> "Saving..."
                    isEditing && isOwner -> "Update"
                    else -> "Save"
                }
            )
        }
    }
}
