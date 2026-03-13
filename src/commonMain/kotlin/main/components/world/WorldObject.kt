package main.components.world

import lib.AbstractMesh
import lib.Color3
import lib.CreatePlaneOptions
import lib.Material
import lib.Mesh
import lib.MeshBuilder
import lib.Node
import lib.PBRMaterial
import lib.PBRCustomMaterial
import lib.Scene
import lib.Texture
import lib.TextureOptions
import lib.TransformNode
import lib.Vector2
import lib.Vector3
import lib.VertexBuffer
import lib.VertexData
import main.components.SceneManager
import main.components.world.effect.WindSystem
import kotlin.js.asDynamic
import kotlin.math.atan2

/**
 * Base class for all objects that can be rendered in the world.
 */
sealed class WorldObject {
    /**
     * Unique identifier for this object
     */
    var id: String = ""

    /**
     * Position in the 3D world
     */
    var position: Vector3 = Vector3(0f, 0f, 0f)

    /**
     * Rotation in the 3D world
     */
    var rotation: Vector3 = Vector3(0f, 0f, 0f)

    /**
     * Scale in the 3D world
     */
    var scale: Vector3 = Vector3(1f, 1f, 1f)

    /**
     * Renders this object in the given scene
     *
     * @param scene The scene to render in
     * @return The created node or null if rendering failed
     */
    abstract fun render(scene: Scene): Node?

    /**
     * Updates the object's state
     *
     * @param deltaTime Time since last update in seconds
     * @param camera Optional camera to use for display direction calculation
     */
    open fun update(deltaTime: Float, camera: lib.ArcRotateCamera? = null) {}

    /**
     * Disposes resources used by this object
     */
    open fun dispose() {}
}

/**
 * A character that can be rendered with a sprite sheet and always faces the camera
 */
class Character(
    var spriteSheet: SpriteSheet,
    var direction: CharacterDirection,
    var activity: PlayerActivity? = null,
    var additionalSpriteSheets: MutableList<SpriteSheet> = mutableListOf(),
) : WorldObject() {

    init {
        // Ensure that additional sprite sheets are correctly sorted (front-to-back)
        additionalSpriteSheets.sortByDescending { it.sorting }
    }

    var skinTone: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            // Update base mesh material color in realtime when skin tone changes
            applySkinToneToBaseMesh()
        }

    private var characterMesh: AbstractMesh? = null

    // List to store meshes for additional sprite sheets
    private var additionalMeshes: MutableList<AbstractMesh> = mutableListOf()

    // Track wind-enabled materials with their sprite sheet for per-frame uniforms
    private data class WindEntry(
        val material: PBRCustomMaterial,
        val sheet: SpriteSheet,
        val mesh: Mesh,
        val basePositions: Array<Float>,
        val halfWidth: Float,
        var gpuActive: Boolean = false,
        var hookPresent: Boolean = false,
        var bindCount: Int = 0,
        var effectNullCount: Int = 0,
        var debugLogCount: Int = 0,
    )

    private val windEntries: MutableList<WindEntry> = mutableListOf()
    private var node: TransformNode? = null
    private var animationTime: Float = 0f

    // The direction to display based on camera angle
    private var displayDirection: CharacterDirection = direction

    // Flag to indicate if the character needs to be re-rendered
    private var needsRerender: Boolean = false

    // Reference to the scene for re-rendering
    private var currentScene: Scene? = null

    // Compute a soft gradient between light and dark skin tones
    private fun computeSkinColor(tone: Float): Color3 {
        val t = tone.coerceIn(0f, 1f)
        // Define light and dark skin anchor colors (RGB, linear-ish)
        val lightR = 1.00f //1.00f
        val lightG = 1.00f //0.86f
        val lightB = 1.00f //0.75f
        val darkR = 0.36f / 10f
        val darkG = 0.24f / 10f
        val darkB = 0.17f / 10f
        val r = lightR + (darkR - lightR) * t
        val g = lightG + (darkG - lightG) * t
        val b = lightB + (darkB - lightB) * t
        return Color3(r, g, b)
    }

    // Apply current skin tone to the base character mesh only
    private fun applySkinToneToBaseMesh() {
        val mesh = characterMesh ?: return
        val material = mesh.material as? PBRMaterial ?: return
        material.albedoColor = computeSkinColor(skinTone)
        material.emissiveColor = computeSkinColor(skinTone)
    }

    override fun render(scene: Scene): Node {
        // Store the scene reference for re-rendering
        currentScene = scene

        // Create a transform node that will act as our container
        val container = TransformNode("character_container_${id}", scene)

        // Set the container's position, rotation, and scale
        container.position = position
        container.rotation = rotation
        container.scaling = scale

        // Clear any existing additional meshes
        additionalMeshes.forEach { it.dispose() }
        additionalMeshes.clear()
        windEntries.clear()

        // Create the main character mesh
        val characterMesh = createMeshForSpriteSheet(
            spriteSheet = spriteSheet,
            name = "character_${id}",
            scene = scene,
            parent = container,
            zOffset = 0f
        )
        this.characterMesh = characterMesh
        // Apply initial skin tone to the base mesh
        applySkinToneToBaseMesh()

        // Create meshes for additional sprite sheets with increasing z-offset
        additionalSpriteSheets.forEachIndexed { index, additionalSpriteSheet ->
            // Use a small z-offset to ensure proper layering (0.01 units per layer)
            val zOffset = (additionalSpriteSheets.lastIndex - index) + 1f
            val additionalMesh = createMeshForSpriteSheet(
                spriteSheet = additionalSpriteSheet,
                name = "character_additional_${index}_${id}",
                scene = scene,
                parent = container,
                zOffset = zOffset
            )
            additionalMeshes.add(additionalMesh)
        }

        // Reset the re-render flag
        needsRerender = false

        this.node = container
        return container
    }

    /**
     * Updates the list of additional sprite sheets
     *
     * @param spriteSheets The new list of sprite sheets
     */
    fun updateSpriteSheets(spriteSheets: List<SpriteSheet>) {
        // Clear the existing list
        additionalSpriteSheets.clear()

        // Add all the new sprite sheets, sorted by descending sorting value (front-to-back)
        additionalSpriteSheets.addAll(spriteSheets.sortedByDescending { it.sorting })

        // Set the flag to re-render on the next update
        needsRerender = true
    }

    override fun update(deltaTime: Float, camera: lib.ArcRotateCamera?) {
        // Check if we need to re-render the character
        if (needsRerender) {
            // Dispose of existing meshes
            characterMesh?.dispose()
            additionalMeshes.forEach { it.dispose() }
            additionalMeshes.clear()
            windEntries.clear()

            // Re-render if we have a scene reference
            currentScene?.let { scene ->
                node?.let { container ->
                    // Create the main character mesh
                    val characterMesh = createMeshForSpriteSheet(
                        spriteSheet,
                        "character_${id}",
                        scene,
                        container,
                        0f
                    )
                    this.characterMesh = characterMesh
                    // Apply initial skin tone to the base mesh after re-render
                    applySkinToneToBaseMesh()

                    // Create meshes for additional sprite sheets with increasing z-offset
                    additionalSpriteSheets.forEachIndexed { index, additionalSpriteSheet ->
                        // Use a small z-offset to ensure proper layering (0.01 units per layer)
                        val zOffset = (additionalSpriteSheets.lastIndex - index) + 1f
                        val additionalMesh = createMeshForSpriteSheet(
                            spriteSheet = additionalSpriteSheet,
                            name = "character_additional_${index}_${id}",
                            scene = scene,
                            parent = container,
                            zOffset = zOffset
                        )
                        additionalMeshes.add(additionalMesh)
                    }

                    // Reset the re-render flag
                    needsRerender = false
                }
            }
        }

        // Calculate the display direction based on camera angle
        if (camera != null) {
            // Use the provided camera
            calculateDisplayDirectionWithCamera(camera)
        } else {
            // Use the default camera from SceneManager
            calculateDisplayDirection()
        }

        // Update animation frame based on direction and movement state
        if (activity == PlayerActivity.Moving) {
            animationTime += deltaTime
        } else {
            animationTime = 0f
        }

        // Always update the sprite animation to reflect the current display direction
        updateSpriteAnimation()

        // Update per-frame wind uniforms for wind-enabled materials
        updateWindUniforms()
    }

    /**
     * Calculates which direction sprite to display based on the relative angle
     * between the player's facing direction and the camera's facing direction.
     *
     * This is the key method that implements the requirement to show the correct
     * sprite direction based on the camera's perspective. For example:
     * - If camera faces north and player faces south, we show south sprite
     * - If camera faces south and player faces north, we also show south sprite
     *
     * The calculation works by finding the angle between the camera's facing direction
     * and the player's facing direction in the horizontal (XZ) plane, then mapping
     * this angle to one of the 8 sprite directions.
     */
    private fun calculateDisplayDirection() {
        // Get the camera controller from SceneManager
        val cameraController = main.components.SceneManager.getCameraController() ?: return
        val camera = cameraController.getCamera()

        // Use the common implementation with the obtained camera
        calculateDisplayDirectionWithCamera(camera)
    }

    /**
     * Calculates which direction sprite to display based on the relative angle
     * between the player's facing direction and the provided camera's facing direction.
     *
     * This version allows passing in a specific camera instead of using the SceneManager.
     *
     * @param camera The camera to use for calculation
     */
    private fun calculateDisplayDirectionWithCamera(camera: lib.ArcRotateCamera) {
        // Convert player direction to a vector
        val playerDirectionVector = directionToVector(direction)

        // Get camera direction (from camera to target)
        val cameraPosition = camera.position
        val cameraTarget = camera.target
        val cameraDirection = cameraTarget.subtract(cameraPosition).normalize()

        // We only care about the horizontal angle (in the XZ plane)
        // Project both vectors onto the XZ plane and normalize them
        val cameraDirectionXZ = Vector3(cameraDirection.x, 0f, cameraDirection.z).normalize()
        val playerDirectionXZ = Vector3(playerDirectionVector.x, 0f, playerDirectionVector.z).normalize()

        // Calculate the dot product of the two vectors
        // For unit vectors, dot product = cos(angle)
        val dotProduct = Vector3.Dot(cameraDirectionXZ, playerDirectionXZ)
        val clampedDot = dotProduct.coerceIn(-1f, 1f)

        // Calculate the angle in radians using the dot product
        // acos(dot product) gives us the angle between the vectors
        val angle = kotlin.math.acos(clampedDot.toDouble()).toFloat()

        // Convert angle to degrees for easier mapping to directions
        val angleDegrees = angle * (180f / kotlin.math.PI.toFloat())

        // Determine if the angle is positive or negative (clockwise or counterclockwise)
        // We need to know if the player is to the left or right of the camera
        // This is equivalent to the Y component of the cross product
        val crossProductY = cameraDirectionXZ.x * playerDirectionXZ.z - cameraDirectionXZ.z * playerDirectionXZ.x
        val isNegativeAngle = crossProductY < 0

        // Adjust the angle based on the sign
        val signedAngleDegrees = if (isNegativeAngle) -angleDegrees else angleDegrees

        // Map the angle to one of the 8 directions
        // The sprite sheet has directions in this order: S, SE, E, NE, N, NW, W, SW
        // We divide the 360 degrees into 8 sectors of 45 degrees each
        // Small epsilon widens S/N buckets slightly to avoid misclassifying near-boundary angles (e.g., 22.8°, -157.2°)
        val eps = 7.5f
        displayDirection = when {
            // S (front-facing): widen by eps
            signedAngleDegrees >= (-22.5f - eps) && signedAngleDegrees < (22.5f + eps) -> CharacterDirection.S
            // SE
            signedAngleDegrees >= (22.5f + eps) && signedAngleDegrees < 67.5f -> CharacterDirection.SE
            // E
            signedAngleDegrees >= 67.5f && signedAngleDegrees < 112.5f -> CharacterDirection.E
            // NE
            signedAngleDegrees >= 112.5f && signedAngleDegrees < (157.5f - eps) -> CharacterDirection.NE
            // N (back-facing): widen by eps
            signedAngleDegrees >= (157.5f - eps) || signedAngleDegrees < (-157.5f + eps) -> CharacterDirection.N
            // NW
            signedAngleDegrees >= (-157.5f + eps) && signedAngleDegrees < -112.5f -> CharacterDirection.NW
            // W
            signedAngleDegrees >= -112.5f && signedAngleDegrees < -67.5f -> CharacterDirection.W
            // SW
            signedAngleDegrees >= -67.5f && signedAngleDegrees < (-22.5f - eps) -> CharacterDirection.SW
            else -> CharacterDirection.S // Default to South if something goes wrong
        }
    }

    /**
     * Converts a CharacterDirection enum to a direction vector.
     */
    private fun directionToVector(dir: CharacterDirection): Vector3 {
        return when (dir) {
            CharacterDirection.N -> Vector3(0f, 0f, 1f)
            CharacterDirection.NE -> Vector3(-0.7071f, 0f, 0.7071f)
            CharacterDirection.E -> Vector3(-1f, 0f, 0f)
            CharacterDirection.SE -> Vector3(-0.7071f, 0f, -0.7071f)
            CharacterDirection.S -> Vector3(0f, 0f, -1f)
            CharacterDirection.SW -> Vector3(0.7071f, 0f, -0.7071f)
            CharacterDirection.W -> Vector3(1f, 0f, 0f)
            CharacterDirection.NW -> Vector3(0.7071f, 0f, 0.7071f)
        }
    }

    /**
     * Updates the sprite animation to show the correct frame based on the current
     * display direction and animation time.
     *
     * This method calculates which frame to show based on the animation time and
     * updates the texture's UV coordinates to display that frame from the sprite sheet.
     */
    private fun updateSpriteAnimation() {
        // Calculate current frame based on animationTime
        val frameIndex = (animationTime / 1000f * spriteSheet.framesPerSecond).toInt() % spriteSheet.getFrameCount()

        // Update UV coordinates on the main character mesh
        characterMesh?.let { mesh ->
            updateMeshUVCoordinates(mesh, spriteSheet, frameIndex)
        }

        // Update UV coordinates on all additional meshes
        additionalSpriteSheets.forEachIndexed { index, additionalSpriteSheet ->
            val additionalMesh = if (index < additionalMeshes.size) additionalMeshes[index] else null
            additionalMesh?.let { mesh ->
                updateMeshUVCoordinates(mesh, additionalSpriteSheet, frameIndex)
            }
        }
    }

    /**
     * Updates the UV coordinates for a mesh to show the correct frame from a sprite sheet
     *
     * @param mesh The mesh to update
     * @param spriteSheet The sprite sheet to use
     * @param frameIndex The frame index to show
     */
    private fun updateMeshUVCoordinates(mesh: AbstractMesh, spriteSheet: SpriteSheet, frameIndex: Int) {
        val material = mesh.material as? PBRMaterial
        material?.albedoTexture?.let { texture ->
            // Get the UV coordinates for the current display direction and frame
            // Pass the activity parameter to use the correct sprite row for sitting
            val uvCoords = spriteSheet.getFrameUV(
                direction = displayDirection,
                isMoving = activity == PlayerActivity.Moving,
                frameIndex = frameIndex,
                activity = activity
            )

            // BabylonJS handles the UV mapping internally when we set these properties
            // Set the UV coordinates using the properly exposed properties
            texture.uOffset = uvCoords.uMin
            texture.vOffset = uvCoords.vMin
            texture.uScale = spriteSheet.cellWidthPx.toFloat() / spriteSheet.imageWidth.toFloat()
            texture.vScale = spriteSheet.cellHeightPx.toFloat() / spriteSheet.imageHeight.toFloat()
        }
    }

    /** Build 5x5 weight grid (row-major). If provided weights (size==25) exist, use them; else default gradient. */
    private fun buildWeights5x5(flippedY: Boolean, provided: FloatArray?): Array<FloatArray> {
        val rows: Array<FloatArray> = Array(5) { FloatArray(5) }
        if (provided != null && provided.size >= 25) {
            var idx = 0
            for (j in 0 until 5) for (i in 0 until 5) rows[j][i] = provided[idx++]
        } else {
            //val rv = floatArrayOf(0f, 0.0125f, 0.025f, 0.5f, 1f)
            val rv = floatArrayOf(0f, 0f, 0f, 0f, 0f)
            for (j in 0 until 5) for (i in 0 until 5) rows[j][i] = rv[j]
        }
        return if (flippedY) rows.reversed().toTypedArray() else rows
    }

    /**
     * Sets all 5x5 wind weight uniforms (w0a/w0b .. w4a/w4b) on the effect and returns the rows used.
     * This centralizes the uniform updates to follow SSOT.
     */
    private fun applyWindWeightUniforms(
        effect: dynamic,
        flippedY: Boolean,
        provided: FloatArray?,
    ): Array<FloatArray> {
        val rows5 = buildWeights5x5(flippedY, provided)
        for (ri in 0 until 5) {
            effect.setVector3("w${ri}a", Vector3(rows5[ri][0], rows5[ri][1], rows5[ri][2]))
            effect.setVector2("w${ri}b", Vector2(rows5[ri][3], rows5[ri][4]))
        }
        return rows5
    }

    /** Configure custom shader code + uniforms for wind displacement. */
    private fun setupWindShader(material: PBRCustomMaterial, spriteSheet: SpriteSheet): Boolean {
        // Register 5x5 uniforms: each row split into vec3 (cols 0..2) and vec2 (cols 3..4)
        for (ri in 0 until 5) {
            material.AddUniform("w${ri}a", "vec3")
            material.AddUniform("w${ri}b", "vec2")
        }
        console.log("[WIND] Registered 5x5 uniforms for material: ${material.name}")
        material.AddUniform("intensityScale", "float")
        material.AddUniform("windDirLocal", "vec3")
        material.AddUniform("windIntensity", "float")
        material.AddUniform("time", "float")
        material.AddUniform("spatialFreq", "float")
        material.AddUniform("temporalFreq", "float")

        // Ensure static uniforms are applied when the material binds (baseline)
        material.onBindObservable.add { _ ->
            val effect = material.getEffect() ?: return@add Unit.also {
                console.error("[WIND] effect is null (material=${material.name})")
            }
            try {
                applyWindWeightUniforms(effect, spriteSheet.windFlipY, spriteSheet.windWeights5x5)
                val outdoors = WorldLoader.isOutdoorsActive()
                val intensity = WindSystem.getIntensity(outdoors)
                // Drive base amplitude via intensityScale (sheet scale only; outdoors gating moved into windIntensity)
                effect.setFloat("intensityScale", spriteSheet.windIntensityScale)
                // Set initial windDirLocal including decayed movement boost
                val camera = SceneManager.getCameraController()?.getCamera()
                val camDir = if (camera != null) {
                    val d = camera.target.subtract(camera.position).normalize()
                    Vector3(d.x, 0f, d.z).normalize()
                } else Vector3(0f, 0f, -1f)
                val yaw = atan2(camDir.x.toDouble(), camDir.z.toDouble()).toFloat()
                val windLocal = WindSystem.rotateY(WindSystem.direction, -yaw)
                val boostWorld = WindSystem.getBoostVector()
                val boostLocal = WindSystem.rotateY(boostWorld, -yaw)
                val windWithBoost = windLocal.add(boostLocal)
                // Boost amplitude based on boost magnitude, even when indoors
                val boostLen = boostLocal.length() * 10.0f
                val windIntensity = intensity + boostLen
                effect.setFloat("windIntensity", windIntensity)
                effect.setVector3("windDirLocal", windWithBoost)
            } catch (e: Throwable) {
                console.error("[WIND] Failed to set baseline uniforms", e)
            }
        }

        // GPU displacement attempt using a late hook (if provided by runtime).
        try {
            val dyn = material.asDynamic()
            if (dyn.Vertex_Before_PositionUpdated != undefined) {
                dyn.Vertex_Before_PositionUpdated(
                    """
                    float u01 = uv.x; float v01 = uv.y;
                    float colf = clamp(floor(u01 * 4.0 + 0.5), 0.0, 4.0);
                    float rowf = clamp(floor(v01 * 4.0 + 0.5), 0.0, 4.0);
                    int col = int(colf);
                    int row = int(rowf);
                    float w = 0.0;
                    if (row == 0) { w = (col==0)? w0a.x : (col==1)? w0a.y : (col==2)? w0a.z : (col==3)? w0b.x : w0b.y; }
                    else if (row == 1) { w = (col==0)? w1a.x : (col==1)? w1a.y : (col==2)? w1a.z : (col==3)? w1b.x : w1b.y; }
                    else if (row == 2) { w = (col==0)? w2a.x : (col==1)? w2a.y : (col==2)? w2a.z : (col==3)? w2b.x : w2b.y; }
                    else if (row == 3) { w = (col==0)? w3a.x : (col==1)? w3a.y : (col==2)? w3a.z : (col==3)? w3b.x : w3b.y; }
                    else { w = (col==0)? w4a.x : (col==1)? w4a.y : (col==2)? w4a.z : (col==3)? w4b.x : w4b.y; }
                    float n = sin(dot(vec2(u01,v01), vec2(1.0, 0.0)) * spatialFreq + time * temporalFreq);
                    n += 0.5 * sin(dot(vec2(u01,v01), vec2(0.5, 0.866)) * spatialFreq * 0.7 + time * temporalFreq * 0.7);
                    float gust = n * 0.66;
                    float amp = 4.0 * (windIntensity * (0.6 + 0.4 * gust) * intensityScale);
                    float sgn = (windDirLocal.x >= 0.0) ? 1.0 : -1.0;
                    positionUpdated.x += sgn * amp * w;
                    """.trimIndent()
                )
                console.log("[WIND] Injected Vertex_Before_PositionUpdated hook for material: ${material.name}")
                return true
            } else {
                console.warn("[WIND] Vertex_MainEnd/Before_PositionUpdated not available for material: ${material.name}")
            }
        } catch (e: Throwable) {
            console.error("[WIND] Error injecting GPU vertex hook for ${material.name}", e)
        }
        return false
    }

    /** Update per-frame wind uniforms for all wind-enabled meshes. */
    private fun updateWindUniforms() {
        val camera = main.components.SceneManager.getCameraController()?.getCamera()
        val camDir = if (camera != null) {
            val d = camera.target.subtract(camera.position).normalize()
            Vector3(d.x, 0f, d.z).normalize()
        } else Vector3(0f, 0f, -1f)

        // Billboard yaw in radians (align local +Z to camera direction)
        val yaw = atan2(camDir.x.toDouble(), camDir.z.toDouble()).toFloat()

        // Rotate world wind into local (billboard) space and add decayed movement boost
        val worldWind = WindSystem.direction
        val windLocal = WindSystem.rotateY(worldWind, -yaw)
        val boostWorld = WindSystem.getBoostVector()
        val boostLocal = WindSystem.rotateY(boostWorld, -yaw)
        val windWithBoost = windLocal.add(boostLocal)

        val outdoors = WorldLoader.isOutdoorsActive()
        val intensity = WindSystem.getIntensity(outdoors = outdoors)
        val t = WindSystem.timeSeconds

        windEntries.forEach { entry ->
            val m = entry.material
            val sheet = entry.sheet
            val mesh = entry.mesh

            val effect = m.getEffect() ?: run {
                entry.effectNullCount += 1
                if (entry.effectNullCount < 5 || entry.effectNullCount % 60 == 0) {
                    console.warn("[WIND] effect is null (material=${m.name}, nullCount=${entry.effectNullCount})")
                }
                // Mark GPU inactive so CPU fallback can take over next frames
                entry.gpuActive = false
                return@forEach
            }
            effect.setVector3("windDirLocal", windWithBoost)
            // Boost amplitude based on boost magnitude; apply even indoors
            val boostLen = boostLocal.length() * 10.0f
            val windIntensity = intensity + boostLen
            effect.setFloat("windIntensity", windIntensity)
            effect.setFloat("time", t)
            effect.setFloat("spatialFreq", WindSystem.spatialFreq)
            effect.setFloat("temporalFreq", WindSystem.temporalFreq)
            // Also ensure static uniforms are set
            // Set 5x5 rows as vec3+vec2 per row
            val weights5 = applyWindWeightUniforms(effect, sheet.windFlipY, sheet.windWeights5x5)
            effect.setFloat("intensityScale", sheet.windIntensityScale)

            if (entry.debugLogCount < 2) {
                console.log(
                    "[WIND] per-frame set (material=" + m.name +
                            ", gpuActive=" + entry.gpuActive +
                            ", windLocalX=" + windWithBoost.x +
                            ", boostLen=" + boostLen +
                            ", windIntensity=" + windIntensity +
                            ", intensityScale=" + (sheet.windIntensityScale) +
                            ", outdoors=" + outdoors +
                            ", w00=" + weights5[0][0] +
                            ", w44=" + weights5[4][4] +
                            ", time=" + t + ")"
                )
                entry.debugLogCount += 1
            }

            // CPU-side sway (fallback only). Run ONLY if GPU is inactive.
            if (!entry.gpuActive) try {
                val base = entry.basePositions
                if (base.isNotEmpty()) {
                    val newPos = base.copyOf()
                    // gust noise
                    val p = 0.0f // no spatial variation for now
                    val gust = kotlin.math.sin((p + t * WindSystem.temporalFreq).toDouble()).toFloat()
                    // amplitude (3x) scaled by windIntensity (allows indoor boosts)
                    val amp = 3.0f * windIntensity * (0.6f + 0.4f * gust) * sheet.windIntensityScale
                    val dirSign = if (windWithBoost.x >= 0f) 1f else -1f
                    // iterate all vertices; derive nearest 5x5 weight for any N x N grid
                    val vertCount = base.size / 3
                    val n = kotlin.math.sqrt(vertCount.toDouble()).toInt().coerceAtLeast(1)
                    var vi = 0
                    for (j in 0 until n) {
                        for (i in 0 until n) {
                            val colIdx = kotlin.math.round((i.toFloat() / (n - 1)) * 4f).toInt().coerceIn(0, 4)
                            val rowIdx = kotlin.math.round((j.toFloat() / (n - 1)) * 4f).toInt().coerceIn(0, 4)
                            val w = weights5[rowIdx][colIdx]

                            // x displacement only (camera-space X via billboard local X)
                            val dx = dirSign * amp * w
                            newPos[vi] = base[vi] + dx
                            // keep Y unchanged to avoid depth changes due to camera tilt
                            newPos[vi + 1] = base[vi + 1]
                            // z unchanged
                            newPos[vi + 2] = base[vi + 2]
                            vi += 3
                        }
                    }
                    mesh.setVerticesData(VertexBuffer.PositionKind, newPos, true)
                    console.log("[WIND] CPU fallback applied (material=${m.name}, amp=" + amp + ", dirSign=" + dirSign + ")")
                }
            } catch (e: Throwable) {
                console.error("[WIND] Failed to update vertex data", e)
                entry.gpuActive = false
            }
            // else: GPU hook present; CPU disabled entirely
        }
    }

    /**
     * Creates a mesh for a sprite sheet with the specified z-offset
     *
     * @param spriteSheet The sprite sheet to use for the mesh
     * @param name The name of the mesh
     * @param scene The scene to create the mesh in
     * @param parent The parent node for the mesh
     * @param zOffset The z-offset to apply to the mesh for layering
     * @return The created mesh
     */
    private fun createMeshForSpriteSheet(
        spriteSheet: SpriteSheet,
        name: String,
        scene: Scene,
        parent: Node,
        zOffset: Float = 0f,
    ): AbstractMesh {
        // Create a material with the sprite sheet texture
        val material: PBRMaterial = if (spriteSheet.windEnabled) {
            PBRCustomMaterial("${name}_material", scene).apply {
                asDynamic().__origResolve = customShaderNameResolve

                // Ensure the vertex shader applies to the depth pre-pass
                val newResolver =
                    js(
                        """
                        (function(shaderName, uniforms, uniformBuffers, samplers, defines, attributes, options) {
                            // --- OPTIONAL: tweak defines/attributes here if you need ---
                            // defines.push("#define MY_FLAG");
                
                            // delegate to the original with the correct `this`
                            return this.__origResolve(shaderName, uniforms, uniformBuffers, samplers, defines, attributes, options);
                        })
                    """
                    ) as (String, Array<String>, Array<String>, Array<String>, Array<String>, Array<String>, dynamic) -> String

                customShaderNameResolve = newResolver
            }
        } else {
            PBRMaterial("${name}_material", scene)
        }
        // Create texture with NEAREST_SAMPLINGMODE for crisp pixel art rendering
        val texture = Texture(spriteSheet.imageUrl, scene, object : TextureOptions {
            override val samplingMode = Texture.NEAREST_SAMPLINGMODE
            override val hasAlpha = true
        })
        material.albedoColor = Color3(1f, 1f, 1f)
        material.albedoTexture = texture
        material.emissiveTexture = texture
        material.emissiveColor = Color3(1f, 1f, 1f)
        material.emissiveIntensity = .5f

        // Enable transparency for the material
        material.backFaceCulling = false
        // Use alpha test + blend to keep crisp sprite edges while allowing smooth alpha changes
        material.transparencyMode = Material.MATERIAL_ALPHATEST
        material.metallic = .5f       // Low metallic value for non-metallic appearance
        material.roughness = .5f      // Medium roughness for slight diffusion of light

        // Create a plane with the correct aspect ratio (vertical billboard-friendly)
        val aspectRatio = spriteSheet.cellHeightPx.toFloat() / spriteSheet.cellWidthPx.toFloat()
        val mesh: Mesh = if (spriteSheet.windEnabled && material is PBRCustomMaterial) {
            createWindGridMesh(name, scene, aspectRatio)
        } else {
            MeshBuilder.CreatePlane(
                name,
                object : CreatePlaneOptions {
                    override val width = 1f / aspectRatio
                    override val height = 1f
                },
                scene
            )
        }

        // Apply depth bias for overlays only (pull towards camera, avoid depth write issues)
        if (zOffset != 0f) {
            material.zOffset = -zOffset
            material.zOffsetUnits = -zOffset
        }

        console.log("[ZOFFSET] zOffset=$zOffset name=$name")

        mesh.material = material

        // Set billboard mode to Y-axis only (rotate around Y-axis)
        mesh.billboardMode = Mesh.BILLBOARDMODE_Y

        // Make the mesh a child of the parent
        mesh.parent = parent

        // Set image dimensions for the sprite sheet (assets are 256x256)
        spriteSheet.setImageDimensions(256, 256)

        // If wind is enabled, attach shader and track material
        if (spriteSheet.windEnabled && material is PBRCustomMaterial) {
            console.log("[WIND] attaching wind shader to $name")
            val hookPresent = setupWindShader(material, spriteSheet)
            val basePos = mesh.getVerticesData(VertexBuffer.PositionKind) ?: arrayOf()
            val entry = WindEntry(
                material = material,
                sheet = spriteSheet,
                mesh = mesh,
                basePositions = basePos.sliceArray(0 until basePos.size),
                halfWidth = (1f / aspectRatio) * 0.5f,
                gpuActive = hookPresent,
                hookPresent = hookPresent
            )
            windEntries.add(entry)

            // Bind-time uniform updates (dynamic + static) and GPU activation flag
            material.onBindObservable.add { _ ->
                val effect = material.getEffect() ?: return@add
                try {
                    // Compute camera-local wind and velocity
                    val camera = SceneManager.getCameraController()?.getCamera()
                    val camDir = if (camera != null) {
                        val d = camera.target.subtract(camera.position).normalize()
                        Vector3(d.x, 0f, d.z).normalize()
                    } else Vector3(0f, 0f, -1f)
                    val yaw = atan2(camDir.x.toDouble(), camDir.z.toDouble()).toFloat()
                    val windLocal = WindSystem.rotateY(WindSystem.direction, -yaw)
                    val outdoors = WorldLoader.isOutdoorsActive()
                    val intensity = WindSystem.getIntensity(outdoors = outdoors)
                    val t = WindSystem.timeSeconds

                    // Compute wind with decayed movement boost (camera-local)
                    val boostWorld = WindSystem.getBoostVector()
                    val boostLocal = WindSystem.rotateY(boostWorld, -yaw)
                    val windWithBoost = windLocal.add(boostLocal)

                    effect.setVector3("windDirLocal", windWithBoost)
                    val boostLen = boostLocal.length()
                    val windIntensity = intensity + boostLen
                    effect.setFloat("windIntensity", windIntensity)
                    effect.setFloat("time", t)
                    effect.setFloat("spatialFreq", WindSystem.spatialFreq)
                    effect.setFloat("temporalFreq", WindSystem.temporalFreq)
                    // Static 5x5 weights (vec3 + vec2 per row)
                    applyWindWeightUniforms(effect, spriteSheet.windFlipY, spriteSheet.windWeights5x5)
                    effect.setFloat("intensityScale", spriteSheet.windIntensityScale)
                    // Mark GPU active only if a GPU hook was successfully injected
                    entry.gpuActive = entry.hookPresent
                    entry.bindCount += 1
                } catch (e: Throwable) {
                    console.error("[WIND] Failed to update wind shader uniforms", e)
                }
            }
        }

        return mesh
    }

    private fun createWindGridMesh(name: String, scene: Scene, aspectRatio: Float): Mesh {
        val mesh = Mesh(name, scene)
        val halfWidth = (1f / aspectRatio) * 0.5f
        val halfHeight = 0.5f

        // 5x5 grid vertices (row-major: bottom->top, left->right)
        val positions = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val steps = 4 // 0..4
        for (j in 0..steps) {
            val fy = j.toFloat() / steps
            val y = -halfHeight + fy * (2f * halfHeight)
            for (i in 0..steps) {
                val fx = i.toFloat() / steps
                val x = -halfWidth + fx * (2f * halfWidth)
                positions.add(x); positions.add(y); positions.add(0f)
                uvs.add(fx); uvs.add(fy)
            }
        }

        val indices = mutableListOf<Int>()
        fun idx(i: Int, j: Int) = j * (steps + 1) + i
        for (j in 0 until steps) {
            for (i in 0 until steps) {
                val i0 = idx(i, j)
                val i1 = idx(i + 1, j)
                val i2 = idx(i, j + 1)
                val i3 = idx(i + 1, j + 1)
                // two triangles: (i0,i1,i2) and (i1,i3,i2)
                indices.add(i0); indices.add(i1); indices.add(i2)
                indices.add(i1); indices.add(i3); indices.add(i2)
            }
        }
        // Flat normals facing +Z
        val normals = MutableList(positions.size) { 0f }.toMutableList()
        var k = 0
        while (k < positions.size) {
            normals[k] = 0f
            normals[k + 1] = 0f
            normals[k + 2] = 1f
            k += 3
        }

        val vd = VertexData()
        vd.positions = positions.toTypedArray()
        vd.indices = indices.toTypedArray()
        vd.normals = normals.toTypedArray()
        vd.uvs = uvs.toTypedArray()
        vd.applyToMesh(mesh, true)
        return mesh
    }

    override fun dispose() {
        characterMesh?.dispose()
        additionalMeshes.forEach { it.dispose() }
        additionalMeshes.clear()
        windEntries.clear()
        node?.dispose()
    }
}

/**
 * A 3D model that can be rendered in the world
 */
class Model(
    var mesh: AbstractMesh,
) : WorldObject() {

    private var modelMesh: AbstractMesh? = null
    private var node: TransformNode? = null

    override fun render(scene: Scene): Node? {
        // Create a transform node that will act as our container
        val container = TransformNode("model_container_${id}", scene)

        // Clone the mesh and make it a child of the container
        val clonedMesh = mesh.clone("model_${id}")
        clonedMesh.parent = container

        // Store references to the meshes
        modelMesh = clonedMesh
        node = container

        return container
    }

    override fun update(deltaTime: Float, camera: lib.ArcRotateCamera?) {
        // Model doesn't need camera for updates
    }

    override fun dispose() {
        modelMesh?.dispose()
        node?.dispose()
    }
}
