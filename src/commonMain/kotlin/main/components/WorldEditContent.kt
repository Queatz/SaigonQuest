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
                val color3 = Color3.FromHexString(hexColor)
                
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
    var backgroundColor by remember { mutableStateOf(currentWorld?.backgroundColor ?: "#ffffff") }
    var directionalLightColor by remember { mutableStateOf(currentWorld?.directionalLightColor ?: "#ffffff") }
    var fogDensity by remember { mutableStateOf(currentWorld?.fogDensity ?: 0.0f) }
    // Outdoors/time-of-day
    var isOutdoors by remember { mutableStateOf(currentWorld?.isOutdoors ?: false) }
    var latitude by remember { mutableStateOf(currentWorld?.latLng?.getOrNull(0)?.toString() ?: "") }
    var longitude by remember { mutableStateOf(currentWorld?.latLng?.getOrNull(1)?.toString() ?: "") }
    
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
                file = file,
                isOutdoors = isOutdoors,
                latLng = run {
                    val lat = latitude.toDoubleOrNull()
                    val lon = longitude.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(lat, lon) else null
                },
                backgroundColor = backgroundColor,
                directionalLightColor = directionalLightColor,
                fogDensity = fogDensity
            )
        } else {
            // Create new world
            World(
                title = title,
                subtitle = subtitle,
                description = description,
                url = url,
                file = file,
                isOutdoors = isOutdoors,
                latLng = run {
                    val lat = latitude.toDoubleOrNull()
                    val lon = longitude.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) listOf(lat, lon) else null
                },
                backgroundColor = backgroundColor,
                directionalLightColor = directionalLightColor,
                fogDensity = fogDensity
            )
        }
        
        coroutineScope.launch {
            try {
                val savedWorld = if (isEditing) {
                    WorldApi.updateWorld(currentWorld!!.key, worldModel)
                } else {
                    WorldApi.createWorld(worldModel)
                }

                // Immediately update lights in the active scene to reflect edited world
                WorldLoader.notifyWorldEdited(savedWorld)
                
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
        
        // File field
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
                Text("File")
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
                    value(file)
                    if (fieldsEnabled) {
                        onInput { event -> file = event.value }
                    }
                    placeholder("Enter world file name (without .glb)")
                    if (!fieldsEnabled) {
                        attr("disabled", "true")
                    }
                }
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
