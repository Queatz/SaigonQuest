package main.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import lib.ArcRotateCamera
import lib.Color4
import lib.Engine
import lib.HemisphericLight
import lib.Scene
import lib.Vector3
import main.components.inventory.InventoryState
import main.components.world.Character
import main.components.world.CharacterDirection
import main.components.world.SpriteSheet
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.outline
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Canvas
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI

/**
 * A component that renders a 3D character model in a separate canvas.
 * 
 * @param characterSpriteSheet The sprite sheet to use for the character
 * @param additionalSpriteSheets List of additional sprite sheets for worn items
 * @param bodyType The body type of the character (Skinny, Normal, Fat)
 * @param skinTone The skin tone of the character (0-1)
 * @param eyeColor The eye color of the character (Black, Blue, Brown, Green, Gray)
 * @param hairStyle The hair style of the character (Short, Medium, Long)
 */
@Composable
fun CharacterRenderer(
    characterSpriteSheet: SpriteSheet = SpriteSheet.createPlayerSpriteSheet(InventoryState.currentUser?.sex),
    additionalSpriteSheets: List<SpriteSheet> = emptyList(),
    bodyType: String = "Normal",
    skinTone: Float = 0.5f,
    eyeColor: String = "Brown",
    hairStyle: String? = "Short"
) {
    // Set up the canvas with CSS and ref instead of ID
    var canvasRef by remember { mutableStateOf<HTMLCanvasElement?>(null) }
    
    // Get the coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // State for worn item sprite sheets
    var wornItemSpriteSheets by remember { mutableStateOf<List<SpriteSheet>>(emptyList()) }
    
    // Keep a reference to the character for live updates
    var characterRef by remember { mutableStateOf<Character?>(null) }
    
    // Load inventory data if not already loaded
    DisposableEffect(Unit) {
        if (InventoryState.inventory == null || InventoryState.items.isEmpty()) {
            coroutineScope.launch {
                InventoryState.loadInventory(
                    coroutineScope = coroutineScope,
                    onSuccess = {
                        // Get the sprite sheets for worn items
                        wornItemSpriteSheets = WornItemsRenderer.getWornItemSpriteSheetsForUser(
                            InventoryState.currentUser,
                            InventoryState.items,
                        )
                    }
                )
            }
        } else {
            // Get the sprite sheets for worn items
            wornItemSpriteSheets = WornItemsRenderer.getWornItemSpriteSheetsForUser(
                InventoryState.currentUser,
                InventoryState.items,
            )
        }
        
        // Register a callback to update worn items when inventory changes
        val refreshCallback = {
            wornItemSpriteSheets = WornItemsRenderer.getWornItemSpriteSheetsForUser(
                InventoryState.currentUser,
                InventoryState.items,
            )
        }
        
        InventoryState.addOnRefreshCallback(refreshCallback)
        
        onDispose {
            InventoryState.removeOnRefreshCallback(refreshCallback)
        }
    }

    // React to skinTone changes in realtime by updating the existing Character
    DisposableEffect(skinTone) {
        characterRef?.skinTone = skinTone
        onDispose { }
    }

    // React to hair and eye changes in realtime by updating additional sprite sheets
    DisposableEffect(hairStyle, eyeColor, wornItemSpriteSheets, additionalSpriteSheets) {
        val hairSpriteSheet = SpriteSheet.createHairSpriteSheet(hairStyle = hairStyle)
        val eyesSpriteSheet = SpriteSheet.createEyesSpriteSheet(
            eyeColor = eyeColor,
            sex = InventoryState.currentUser?.sex,
        )
        val newAdditional = (listOfNotNull(hairSpriteSheet, eyesSpriteSheet) + additionalSpriteSheets + wornItemSpriteSheets)
        characterRef?.updateSpriteSheets(newAdditional)
        onDispose { }
    }

    // React to body type changes in realtime by updating the base sprite sheet and re-rendering
    DisposableEffect(bodyType) {
        val newBase = SpriteSheet.createPlayerSpriteSheetForBodyType(
            sex = InventoryState.currentUser?.sex,
            bodyType = bodyType,
        )
        characterRef?.let { character ->
            character.spriteSheet = newBase
            // Trigger re-render by updating sprite sheets with the current list
            character.updateSpriteSheets(character.additionalSpriteSheets.toList())
        }
        onDispose { }
    }

    Canvas(
        attrs = {
            style {
                width(120.px)
                height(180.px)
                display(DisplayStyle.Block)
                margin(8.px, 0.px)
                backgroundColor(Color.transparent)
                outline("none")
            }
            
            // Use ref instead of id
            ref { canvas ->
                canvasRef = canvas
                
                onDispose {
                    // Clean up any references if needed
                }
            }
        }
    )
    
    // Set up BabylonJS scene using the canvas reference
    DisposableEffect(canvasRef) {
        // Only proceed if we have a valid canvas reference
        val canvas = canvasRef ?: return@DisposableEffect onDispose {}
        
        // Create the engine with alpha enabled for transparent background
        val engine = Engine(canvas, antialias = true)
        
        // Create the scene with transparent background
        val scene = Scene(engine)
        scene.clearColor = Color4(0f, 0f, 0f, 0f) // Transparent background
        
        // Create a hemisphere light for soft, ambient lighting
        val light = HemisphericLight("light", Vector3(0f, 1f, 0f), scene)
        light.intensity = 0.7f
        
        // Create an arc rotate camera with fixed height
        val camera = ArcRotateCamera(
            name = "camera",
            alpha = (PI / 2f).toFloat(),
            beta = (PI / 3f).toFloat(),
            radius = 2f,
            target = Vector3.Zero(),
            scene = scene
        ).apply {
            attachControl()

            // Disable only keyboard controls to avoid conflicts with player movement
            inputs.removeByType("ArcRotateCameraKeyboardMoveInput")
            
            // Set a narrow FOV for a more portrait-like view
            fov = 0.5f
            minZ = 0.1f
            maxZ = 10f
            
            // Lock the camera position
            lowerRadiusLimit = radius
            upperRadiusLimit = radius
            lowerBetaLimit = (15f * PI / 180f).toFloat() // 85 degrees in radians
            upperBetaLimit = (85f * PI / 180f).toFloat() // 85 degrees in radians
        }
        
        // Create hair sprite sheet for preview if available
        val hairSpriteSheet = SpriteSheet.createHairSpriteSheet(hairStyle = hairStyle)
        // Create eyes sprite sheet for preview based on selected eyeColor and current user's sex
        val eyesSpriteSheet = SpriteSheet.createEyesSpriteSheet(
            eyeColor = eyeColor,
            sex = InventoryState.currentUser?.sex,
        )

        // Select base sprite sheet based on sex and body type
        val baseSpriteSheet = SpriteSheet.createPlayerSpriteSheetForBodyType(
            sex = InventoryState.currentUser?.sex,
            bodyType = bodyType,
        )

        // Create the character
        val character = Character(
            spriteSheet = baseSpriteSheet,
            direction = CharacterDirection.S, // Default facing south
            activity = null,
            additionalSpriteSheets = (listOfNotNull(eyesSpriteSheet, hairSpriteSheet) + additionalSpriteSheets + wornItemSpriteSheets).toMutableList()
        )
        // Initialize skin tone
        character.skinTone = skinTone
        // Keep reference for live updates
        characterRef = character
        
        // Render the character
        character.render(scene)
        
        // Start the render loop
        engine.runRenderLoop {
            // Update the character with the specific camera to ensure it renders correctly when camera moves
            character.update(scene.deltaTime, camera)
            scene.render()
        }
        
        // Clean up when the component is disposed
        onDispose {
            scene.dispose()
            engine.dispose()
        }
    }
}
