package main.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document
import lib.ArcRotateCamera
import lib.Color4
import lib.Engine
import lib.HemisphericLight
import lib.Scene
import lib.Vector3
import main.components.world.Character
import main.components.world.CharacterDirection
import main.components.world.PlayerActivity
import main.components.world.SpriteSheet
import main.theme.Styles
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Canvas
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI

/**
 * A component that renders a 3D character model in a separate canvas.
 * 
 * @param characterSpriteSheet The sprite sheet to use for the character
 * @param bodyType The body type of the character (Skinny, Normal, Fat)
 * @param skinTone The skin tone of the character (0-1)
 * @param eyeColor The eye color of the character (Black, Blue, Brown, Green, Gray)
 * @param hairStyle The hair style of the character (Short, Medium, Long)
 */
@Composable
fun CharacterRenderer(
    characterSpriteSheet: SpriteSheet = SpriteSheet.createPlayerSpriteSheet(),
    bodyType: String = "Normal",
    skinTone: Float = 0.5f,
    eyeColor: String = "Brown",
    hairStyle: String = "Short"
) {
    // Set up the canvas with CSS and ref instead of ID
    var canvasRef: HTMLCanvasElement? = null
    
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
            lowerRadiusLimit = 4f // Min constraint
            upperRadiusLimit = 16f // Max constraint
            lowerBetaLimit = (15f * PI / 180f).toFloat() // 85 degrees in radians
            upperBetaLimit = (85f * PI / 180f).toFloat() // 85 degrees in radians
        }
        
        // Create the character
        val character = Character(
            spriteSheet = characterSpriteSheet,
            direction = CharacterDirection.S, // Default facing south
            isMoving = false,
            activity = null
        )
        
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
