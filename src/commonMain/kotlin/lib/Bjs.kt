@file:JsModule("@babylonjs/core")
@file:JsNonModule

package lib

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.KeyboardEvent

/**
 * Interface for mesh metadata
 */
external interface MeshMetadata {
    /**
     * Original material of the mesh (used for restoring after effects)
     */
    var originalMaterial: Material?
}

abstract external class AbstractMesh {
    var isVisible: Boolean
    var position: Vector3
    var material: Material
    var receiveShadows: Boolean
    var rotation: Vector3
    var rotationQuaternion: Quaternion?
    var origin: Vector3
    var scaling: Vector3
    var billboardMode: Int
    val onAfterWorldMatrixUpdateObservable: Observable<AbstractMesh>
    var instancedBuffers: dynamic
    var actionManager: ActionManager?
    var name: String
    var metadata: MeshMetadata?

    fun getVerticesData(kind: String): Array<Float>?
    fun setVerticesData(kind: String, data: Array<Float>, updatable: Boolean)
    fun clone(name: String): AbstractMesh
    fun registerInstancedBuffer(name: String, size: Int)
    fun addRotation(x: Float, y: Float, z: Float)
    fun dispose()
    
    /**
     * Gets the bounding info of the mesh
     * @return The bounding info of the mesh
     */
    fun getBoundingInfo(): BoundingInfo
}

external class CascadedShadowGenerator(
    mapSize: Int,
    light: DirectionalLight,
    useFullFloatEngine: Boolean = definedExternally
) {
    var lambda: Float
    var bias: Float
    var normalBias: Float
    var transparencyShadow: Boolean
    var stabilizeCascades: Boolean
    var shadowMaxZ: Float

    fun splitFrustum()
    fun addShadowCaster(mesh: AbstractMesh)
    fun removeShadowCaster(mesh: AbstractMesh)
}

external class Color3(r: Float = definedExternally, g: Float = definedExternally, b: Float = definedExternally) {
    fun toColor4(): Color4
    fun toGammaSpace(): Color3
    fun toLinearSpace(): Color3
    fun toLinearSpaceToRef(ref: Color3): Color3
    fun toGammaSpaceToRef(ref: Color3): Color3
    fun scale(scale: Float): Color3

    companion object {
        fun FromHexString(hex: String): Color3
        fun White(): Color3
        fun Black(): Color3
    }
}

external class Color4(
    var r: Float = definedExternally,
    var g: Float = definedExternally,
    var b: Float = definedExternally,
    var a: Float = definedExternally
) {
    fun toLinearSpace(): Color4
    fun scale(scale: Float): Color4
    fun subtract(color: Color4): Color4
    fun asArray(): Array<Float>
}

external class DirectionalLight(name: String, direction: Vector3, scene: Scene) {
    var intensity: Float
    var diffuse: Color3
    var shadowMinZ: Float
    var shadowMaxZ: Float
    var position: Vector3
    var direction: Vector3
}

external class HemisphericLight(name: String, direction: Vector3, scene: Scene) {
    var diffuse: Color3
    var groundColor: Color3
    var intensity: Float
}

external class Scene(engine: Engine? = definedExternally) {
    var fogMode: Int
    var fogDensity: Float
    var fogColor: Color3
    var clearColor: Color4
    var ambientColor: Color3
    var activeCamera: Camera?
    var pointerX: Int
    var pointerY: Int
    var deltaTime: Float
    val onKeyboardObservable: Observable<KeyboardInfo>
    val onPointerObservable: Observable<PointerInfo>

    /**
     * Gets the engine that created this scene
     * @return The engine that created this scene
     */
    fun getEngine(): Engine
    
    /**
     * Gets the transform matrix used to project to screen space
     * @return The transform matrix
     */
    fun getTransformMatrix(): Matrix
    
    fun enableDepthRenderer(camera: Camera? = definedExternally): DepthRenderer
    fun disableDepthRenderer()
    fun getDepthRenderer(): DepthRenderer?
    fun render()
    fun dispose()
    fun createPickingRay(x: Int, y: Int, world: Matrix, camera: Camera? = definedExternally): Ray
    fun registerBeforeRender(callback: () -> Unit)
    fun getFrameId(): Int
    fun removeMesh(mesh: AbstractMesh)

    companion object {
        val FOGMODE_EXP2: Int
    }
}
external class Mesh(name: String = definedExternally, scene: Scene = definedExternally) : AbstractMesh {
    fun createInstance(name: String): Mesh

    companion object {
        val BILLBOARDMODE_Y: Int
        val BILLBOARDMODE_ALL: Int
        val ALPHA_DISABLE: Int
        val ALPHA_ADD: Int
        val ALPHA_COMBINE: Int
        val ALPHA_SUBTRACT: Int
        val ALPHA_MULTIPLY: Int
        val ALPHA_MAXIMIZED: Int
        val ALPHA_ONEONE: Int
        val ALPHA_PREMULTIPLIED: Int
        val ALPHA_PREMULTIPLIED_PORTERDUFF: Int
        val ALPHA_COMPLEX: Int
    }
}
/**
 * LinesMesh represents a dynamic line in 3D space created via MeshBuilder.CreateLines.
 */
external class LinesMesh(name: String = definedExternally, scene: Scene = definedExternally) : AbstractMesh {
    /**
     * Color of the line mesh. Controls RGBA of the line.
     */
    var color: Color4
    /**
     * Update the vertex positions of the line mesh. Expects flat float array [x,y,z,...].
     */
    fun updateVerticesData(kind: String, data: Array<Float>, updateExtends: Boolean = definedExternally)
}

external class MeshBuilder {
    companion object {
        fun CreateGround(name: String, options: CreateGroundOptions = definedExternally, scene: Scene = definedExternally): Mesh
        fun CreatePlane(name: String, options: CreatePlaneOptions = definedExternally, scene: Scene = definedExternally): Mesh
        fun CreateSphere(name: String, options: CreateSphereOptions = definedExternally, scene: Scene = definedExternally): Mesh
        /**
         * Create a tube along the given path to represent a thick line.
         * Options: { path: Array<Vector3>, radius: Float, updatable?: Boolean }
         */
        fun CreateTube(name: String, options: dynamic = definedExternally, scene: Scene = definedExternally): Mesh
        /**
         * Create a dynamic line mesh in 3D space.
         * Options: { points: Array<Vector3>, updatable: Boolean }
         */
        fun CreateLines(name: String, options: dynamic = definedExternally, scene: Scene = definedExternally): LinesMesh
        /**
         * Create a box mesh.
         * Options: { width: Float, height: Float, depth: Float, updatable?: Boolean }
         */
        fun CreateBox(name: String, options: dynamic = definedExternally, scene: Scene = definedExternally): Mesh
        /**
         * Create a disc mesh.
         * Options: { radius: Float, tessellation: Int, updatable?: Boolean }
         */
        fun CreateDisc(name: String, options: dynamic = definedExternally, scene: Scene = definedExternally): Mesh
    }
}

abstract external class Material {
    var backFaceCulling: Boolean
    var alphaMode: Int
    var needDepthPrePass: Boolean
    var wireframe: Boolean
    var zOffset: Float
    var zOffsetUnits: Float
    var cullBackFaces: Boolean
    var transparencyMode: Int
    var alphaTestCutoff: Float

    var disableColorWrite: Boolean
    var disableDepthWrite: Boolean
    var alpha: Float
    var id: String
    var name: String

    fun clone(name: String): Material
    fun dispose(forceDisposeEffect: Boolean = definedExternally)
    fun isReady(): Boolean

    companion object {
        val MATERIAL_ALPHATEST: Int
        val MATERIAL_OPAQUE: Int
        val MATERIAL_ALPHABLEND: Int
        val MATERIAL_ALPHATESTANDBLEND: Int
    }
}

external class StandardMaterial(name: String, scene: Scene) : Material {
    var diffuseTexture: Texture
    var emissiveTexture: Texture
    var opacityTexture: Texture
    var diffuseColor: Color3
    var specularColor: Color3
    var emissiveColor: Color3
    var roughness: Float
    var disableLighting: Boolean
    var useAlphaFromDiffuseTexture: Boolean
}

/**
 * Represents a texture in the scene.
 * 
 * @param url The URL of the texture
 * @param scene The scene the texture belongs to
 * @param options Options to configure the texture
 */
open external class Texture(url: String, scene: Scene, options: TextureOptions = definedExternally) {
    var hasAlpha: Boolean
    var wrapU: Int
    var wrapV: Int
    var url: String
    var uOffset: Float
    var vOffset: Float
    var uScale: Float
    var vScale: Float
    val samplingMode: Int

    fun dispose()

    companion object {
        val NEAREST_SAMPLINGMODE: Int
        val BILINEAR_SAMPLINGMODE: Int
        val TRILINEAR_SAMPLINGMODE: Int
        val NEAREST_NEAREST_MIPLINEAR: Int
        val NEAREST_LINEAR_MIPNEAREST: Int
        val NEAREST_LINEAR_MIPLINEAR: Int
        val NEAREST_LINEAR: Int
        val NEAREST_NEAREST: Int
        val LINEAR_NEAREST_MIPNEAREST: Int
        val LINEAR_NEAREST_MIPLINEAR: Int
        val LINEAR_LINEAR_MIPNEAREST: Int
        val LINEAR_LINEAR_MIPLINEAR: Int
        val CLAMP_ADDRESSMODE: Int
    }
}

external class Engine(
    canvas: HTMLCanvasElement = definedExternally,
    antialias: Boolean = definedExternally,
    options: dynamic = definedExternally,
    adaptToDeviceRatio: Boolean = definedExternally
) {
    fun runRenderLoop(callback: () -> Unit)
    fun setHardwareScalingLevel(scalingLevel: Int)
    fun resize()
    fun dispose()
    fun getRenderWidth(): Int
    fun getRenderHeight(): Int

    companion object {
        val TEXTUREFORMAT_ALPHA: Int
    }
}

external class Observable<T> {
    fun add(callback: (T) -> Unit)
}

external class ShadowGenerator(mapSize: Int, light: DirectionalLight) {
    fun addShadowCaster(mesh: AbstractMesh)
    fun removeShadowCaster(mesh: AbstractMesh)
}

/**
 * Class representing the bounding information of a mesh
 */
external class BoundingInfo {
    /**
     * The minimum vector of the bounding box
     */
    val minimum: Vector3
    
    /**
     * The maximum vector of the bounding box
     */
    val maximum: Vector3
    
    /**
     * Checks if a point is inside the bounding box
     * 
     * @param point The point to check
     * @return True if the point is inside the bounding box, false otherwise
     */
    fun isInside(point: Vector3): Boolean
}

/**
 * Class representing a vector with 2 coordinates (x, y)
 */
external class Vector2(x: Float = definedExternally, y: Float = definedExternally) {
    var x: Float
    var y: Float
    
    fun normalize(): Vector2
    fun length(): Float
    fun clone(): Vector2
    
    companion object {
        fun Zero(): Vector2
    }
}

/**
 * Class representing a vector with 3 coordinates (x, y, z)
 */
external class Vector3(x: Float = definedExternally, y: Float = definedExternally, z: Float = definedExternally) {
    var x: Float
    var y: Float
    var z: Float

    fun normalize(): Vector3
    fun copyFrom(source: Vector3): Vector3
    fun length(): Float
    fun scaleInPlace(scale: Float): Vector3
    fun add(otherVector: Vector3): Vector3
    fun addInPlace(otherVector: Vector3): Vector3
    fun multiply(otherVector: Vector3): Vector3
    fun multiplyInPlace(otherVector: Vector3): Vector3
    fun scale(scale: Float): Vector3
    fun subtract(otherVector: Vector3): Vector3
    fun floor(): Vector3
    fun equals(otherVector: Vector3): Boolean
    fun projectOnPlaneToRef(plane: Plane, result: Vector3, origin: Vector3)
    fun clone(): Vector3

    companion object {
        fun Zero(): Vector3
        fun Forward(): Vector3
        fun Right(): Vector3
        fun Up(): Vector3
        fun Down(): Vector3
        fun Left(): Vector3
        fun Backward(): Vector3
        fun TransformCoordinates(vector: Vector3, transformation: Matrix): Vector3
        fun Distance(v1: Vector3, v2: Vector3): Float
        fun Lerp(start: Vector3, end: Vector3, amount: Float): Vector3
        fun Dot(left: Vector3, right: Vector3): Float
        
        /**
         * Projects a 3D point to screen space
         * 
         * @param vector The vector to project
         * @param world The world matrix
         * @param transform The transformation matrix
         * @param viewport The viewport
         * @return The projected vector
         */
        fun Project(vector: Vector3, world: Matrix, transform: Matrix, viewport: Viewport): Vector3
    }
}

external class Matrix {
    fun invert(): Matrix

    companion object {
        fun Identity(): Matrix
    }
}

external class Quaternion {
    fun multiplyInPlace(q1: Quaternion): Quaternion
    fun toEulerAngles(): Vector3
    fun clone(): Quaternion

    companion object {
        fun Identity(): Quaternion
        fun FromLookDirectionRHToRef(direction: Vector3, up: Vector3, result: Quaternion): Quaternion
        fun FromLookDirectionLH(direction: Vector3, up: Vector3): Quaternion
        fun SlerpToRef(start: Quaternion, end: Quaternion, amount: Float, result: Quaternion): Unit
        fun RotationAxis(axis: Vector3, angle: Float): Quaternion
    }
}

/**
 * Class representing a viewport in screen space
 */
external class Viewport(x: Float, y: Float, width: Float, height: Float) {
    var x: Float
    var y: Float
    var width: Float
    var height: Float
    
    /**
     * Converts viewport coordinates to global screen coordinates
     * 
     * @param renderWidth The width of the render target
     * @param renderHeight The height of the render target
     * @return The global viewport
     */
    fun toGlobal(renderWidth: Int, renderHeight: Int): Viewport
}

abstract external class Camera {
    var minZ: Float
    var maxZ: Float
    var position: Vector3
    var fov: Float
    var globalPosition: Vector3
    var viewport: Viewport

    fun getDirection(direction: Vector3): Vector3
    fun getForwardRay(length: Float = definedExternally): Ray
}

external class ArcRotateCamera(
    name: String,
    alpha: Float,
    beta: Float,
    radius: Float,
    target: Vector3,
    scene: Scene
) : Camera {
    var alpha: Float
    var beta: Float
    var radius: Float
    var target: Vector3
    var zoomToMouseLocation: Boolean
    var wheelDeltaPercentage: Float
    var wheelPrecision: Float
    var lowerRadiusLimit: Float
    var upperRadiusLimit: Float
    var lowerAlphaLimit: Float
    var upperAlphaLimit: Float
    var lowerBetaLimit: Float
    var upperBetaLimit: Float
    var inertialRadiusOffset: Float
    var inertialAlphaOffset: Float
    var inertialBetaOffset: Float
    var targetScreenOffset: Vector3
    var inputs: ArcRotateCameraInputs

    fun attachControl(noPreventDefault: Boolean = definedExternally)
    fun detachControl()
    fun getViewMatrix(): Matrix
    fun setTarget(target: Vector3)
}

external class ArcRotateCameraInputs {
    val removeByType: (String) -> Unit
}

external class ArcRotateCameraKeyboardMoveInput {
    var angularSpeed: Float
}

external class KeyboardEventTypes {
    companion object {
        val KEYDOWN: Number
        val KEYUP: Number
    }
}

external class KeyboardInfo {
    val type: Number
    val event: KeyboardEvent
}

external class PointerEventTypes {
    companion object {
        val POINTERDOWN: Number
        val POINTERUP: Number
        val POINTERMOVE: Number
        val POINTERWHEEL: Number
        val POINTERPICK: Number
        val POINTERTAP: Number
        val POINTERDOUBLETAP: Number
    }
}

external class PointerInfo {
    val type: Number
    val event: PointerEvent
}

external class PointerEvent {
    val shiftKey: Boolean
    val ctrlKey: Boolean
    val altKey: Boolean
    val offsetX: Float
    val offsetY: Float
}

external class ActionManager(scene: Scene) {
    fun registerAction(action: ExecuteCodeAction)
}

external class ExecuteCodeAction(
    trigger: Number,
    action: () -> Unit
)

external class Ray {
    var origin: Vector3
    var direction: Vector3

    fun intersectsMesh(mesh: AbstractMesh): PickingInfo
}

external class PickingInfo {
    val pickedPoint: Vector3?
}

external class Plane {
    companion object {
        fun FromPositionAndNormal(position: Vector3, normal: Vector3): Plane
    }
}

external class VertexBuffer {
    companion object {
        val PositionKind: String
    }
}

external class VertexData {
    var positions: Array<Float>?
    var indices: Array<Int>?
    var normals: Array<Float>?
    var uvs: Array<Float>?

    fun applyToMesh(mesh: Mesh, updatable: Boolean)

    companion object {
        fun ComputeNormals(positions: Array<Float>, indices: Array<Int>, normals: Array<Float>)
    }
}

abstract external class PostProcess {
    fun dispose()
}

external class ColorCorrectionPostProcess(name: String, colorTableUrl: String, ratio: Float, camera: Camera) : PostProcess

external class DefaultRenderingPipeline(
    name: String,
    hdr: Boolean,
    scene: Scene,
    cameras: Array<Camera?>
) {
    var depthOfFieldEnabled: Boolean
    var depthOfField: DepthOfFieldEffect
    var depthOfFieldBlurLevel: Int
    var bloomEnabled: Boolean
    var bloomThreshold: Float
    var bloomWeight: Float
    var bloomKernel: Float
    var bloomScale: Float
    var grainEnabled: Boolean
    /**
     * The grain effect configuration object
     */
    var grain: GrainEffect
    var sharpenEnabled: Boolean
    var sharpen: ImageProcessingPostProcess
    /**
     * Enables image processing
     */
    var imageProcessingEnabled: Boolean
    /**
     * The image processing configuration
     */
    var imageProcessing: ImageProcessingConfiguration
    /**
     * Enables tone mapping
     */
    var toneMappingEnabled: Boolean
    /**
     * Enables fog rendering in the pipeline
     */
    var fogEnabled: Boolean
}

external class DepthOfFieldEffect {
    var fStop: Float
    var focalLength: Float
    var focusDistance: Float
}

/**
 * The ImageProcessingPostProcess is used to apply image processing effects to the scene.
 * It can be used for various effects including vignette, color grading, etc.
 */
external class ImageProcessingPostProcess {
    /**
     * The amount of edge enhancement to apply
     */
    var edgeAmount: Float
    
    /**
     * The amount of color enhancement to apply
     */
    var colorAmount: Float
    
    /**
     * Whether the vignette effect is enabled
     */
    var vignetteEnabled: Boolean
    
    /**
     * The weight of the vignette effect (0.0-10.0)
     * Higher values create a stronger darkening effect at the edges
     */
    var vignetteWeight: Float
    
    /**
     * Controls how far the vignette extends from the edges (0.0-10.0)
     * Higher values make the vignette cover more of the screen
     */
    var vignetteStretch: Float
    
    /**
     * The color of the vignette
     */
    var vignetteColor: Color4
    
    /**
     * The vignette effect configuration
     * @deprecated Use vignetteWeight, vignetteStretch, and vignetteColor directly instead
     */
    var vignette: VignetteEffect
    
    /**
     * Creates a new ImageProcessingPostProcess
     * 
     * @param name The name of the post process
     * @param ratio The ratio of the post process (1.0 for full resolution)
     * @param camera The camera to attach the post process to
     * @param samplingMode The sampling mode to use (default: Texture.BILINEAR_SAMPLINGMODE)
     * @param engine The engine to use for rendering (default: null, uses current engine)
     * @param reusable Whether the post process can be reused (default: false)
     * @param textureType The type of texture to use (default: Engine.TEXTURETYPE_UNSIGNED_INT)
     */
    constructor(
        name: String, 
        ratio: Float, 
        camera: Camera, 
        samplingMode: Int = definedExternally,
        engine: Engine? = definedExternally,
        reusable: Boolean = definedExternally,
        textureType: Int = definedExternally
    )
}

/**
 * The ImageProcessingConfiguration class is used to configure image processing effects.
 */
external class ImageProcessingConfiguration {
    /**
     * The contrast level (0.0-2.0)
     */
    var contrast: Float
    
    /**
     * The exposure level (0.0-2.0)
     */
    var exposure: Float
}

/**
 * The GrainEffect is used to add film grain to the rendering.
 */
external class GrainEffect {
    /**
     * The intensity of the grain effect (0.0-1.0)
     */
    var intensity: Float
}

/**
 * The VignetteEffect is used to add a vignette effect to the rendering.
 * This darkens the edges of the screen to create a frame-like appearance.
 */
external interface VignetteEffect {
    /**
     * The intensity of the vignette effect (0.0-1.0)
     * Higher values create a stronger darkening effect at the edges
     */
    var intensity: Float
    
    /**
     * Controls how far the vignette extends from the edges (0.0-1.0)
     * Higher values make the vignette cover more of the screen
     */
    var stretch: Float
    
    /**
     * The color of the vignette (typically black)
     */
    var color: Color4
}

external class DepthOfFieldEffectBlurLevel {
    companion object {
        val Low: Int
        val Medium: Int
        val High: Int
    }
}

/**
 * The DepthRenderer is used to generate depth textures for a scene.
 * These textures can be used for various effects like depth of field, SSAO, etc.
 */
external class DepthRenderer(scene: Scene, type: Int = definedExternally) {
    /**
     * Gets the depth map texture from the renderer.
     * This texture contains the depth values of the scene from the camera's perspective.
     */
    fun getDepthMap(): Texture
    
    /**
     * Disposes the depth renderer and releases its resources.
     */
    fun dispose()
    
    companion object {
        /**
         * RGBA texture type.
         */
        val DEPTH_TEXTURE_TYPE: Int
        
        /**
         * Depth texture type.
         */
        val DEPTH_RENDERER_DEPTH_TYPE: Int
    }
}

/**
 * The Effect class represents a shader effect (vertex and fragment shader pair).
 * It is used to apply custom shader code to materials.
 */
external class Effect {
    /**
     * Sets a float value on the shader
     */
    fun setFloat(name: String, value: Float)
    
    /**
     * Sets a Vector2 value on the shader
     */
    fun setVector2(name: String, value: Vector2)
    
    /**
     * Sets a Vector3 value on the shader
     */
    fun setVector3(name: String, value: Vector3)
    
    /**
     * Sets a Matrix value on the shader
     */
    fun setMatrix(name: String, matrix: Matrix)
    
    /**
     * Sets a texture on the shader
     */
    fun setTexture(name: String, texture: Texture)

    /**
     * Gets the fragment shader source code
     * @return The fragment shader source code as a string
     */
    fun getFragmentShaderSource(): String
    
    fun isReady(): Boolean
}

/**
 * The ShaderStore is a static object that manages shader code.
 * This is the main repository for all shader code used in BabylonJS.
 */
external object ShaderStore {
    /**
     * Stores the shader code for vertex shaders
     * Access using: ShaderStore.ShadersStore["shaderName" + "VertexShader"]
     */
    val ShadersStore: dynamic
    
    /**
     * Stores the shader code for fragment shaders
     * Access using: ShaderStore.ShadersStore["shaderName" + "PixelShader"]
     */
    val IncludesShadersStore: dynamic
}

external class LensRenderingPipeline(
    name: String,
    parameters: dynamic,
    scene: Scene,
    ratio: Float,
    cameras: Array<Camera>
)

/**
 * Interface for SSAO (Screen Space Ambient Occlusion) options.
 * Used to configure the SSAO2RenderingPipeline.
 */
external interface SSAOOptions {
    /**
     * The radius of the SSAO effect (0.0-1.0)
     * Lower values create more subtle effect
     */
    val radius: Float
    
    /**
     * The strength of the SSAO effect (0.0-1.0)
     * Lower values create more subtle effect
     */
    val totalStrength: Float
    
    /**
     * The number of samples used for the SSAO calculation
     * Higher values provide better quality but lower performance
     */
    val samples: Int
    
    /**
     * The maximum Z depth for the SSAO effect
     * Should match camera maxZ
     */
    val maxZ: Float
}

/**
 * Render pipeline to produce SSAO (Screen Space Ambient Occlusion) effect.
 * Properties should be set after creating the instance.
 */
external class SSAO2RenderingPipeline(
    name: String,
    scene: Scene,
    ratio: dynamic = definedExternally,
    cameras: Array<Camera> = definedExternally,
    forceGeometryBuffer: Boolean = definedExternally,
    textureType: Int = definedExternally
) {
    /**
     * The radius around the analyzed pixel used by the SSAO post-process. Default value is 2.0
     */
    var radius: Float
    
    /**
     * The output strength of the SSAO post-process. Default value is 1.0.
     */
    var totalStrength: Float
    
    /**
     * Number of samples used for the SSAO calculations. Default value is 8.
     */
    var samples: Int
    
    /**
     * If bilateral blur should be used
     */
    var bypassBlur: Boolean
    
    /**
     * Number of samples used for bilateral filtering
     */
    var bilateralSamples: Int
    
    /**
     * Maximum depth value to still render AO. Default value is 100.0.
     */
    var maxZ: Float
    
    /**
     * In order to save performances, SSAO radius is clamped on close geometry. This ratio changes by how much.
     * Default value is 0.2.
     */
    var minZAspect: Float
    
    /**
     * The base color of the SSAO post-process. The final result is "base + ssao" between [0, 1].
     * Default value is 0.
     */
    var base: Float
    
    /**
     * Used in SSAO calculations to compensate for accuracy issues with depth values.
     * Default value is 0.02.
     */
    var epsilon: Float
}

external interface CreatePlaneOptions {
    val size: Float? get() = definedExternally
    val updatable: Boolean? get() = definedExternally
    val width: Float? get() = definedExternally
    val height: Float? get() = definedExternally
}

external interface CreateSphereOptions {
    val diameter: Float? get() = definedExternally
    val segments: Int? get() = definedExternally
    val updatable: Boolean? get() = definedExternally
}

external interface CreateGroundOptions {
    val width: Float? get() = definedExternally
    val height: Float? get() = definedExternally
    val subdivisions: Int? get() = definedExternally
}

/**
 * Interface for texture creation options.
 * Used to configure textures when creating a new Texture instance.
 */
external interface TextureOptions {
    /**
     * The sampling mode to be used by the texture.
     * Determines how the texture is sampled when rendered.
     * Use constants like NEAREST_SAMPLINGMODE for pixel art or TRILINEAR_SAMPLINGMODE for smooth textures.
     */
    val samplingMode: Int? get() = definedExternally
    
    /**
     * Defines if the texture should build mip maps.
     * Set to true to disable mipmap generation.
     * Default is false.
     */
    val noMipmap: Boolean? get() = definedExternally
    
    /**
     * Defines if the texture should be flipped on the Y axis during loading.
     * Default is true for BabylonJS to match WebGL requirements.
     */
    val invertY: Boolean? get() = definedExternally
    
    /**
     * Defines if the texture contains alpha values that should be used.
     * Default is false.
     */
    val hasAlpha: Boolean? get() = definedExternally
    
    /**
     * Defines if mip maps should be generated for this texture.
     * Default is true.
     */
    val generateMipMaps: Boolean? get() = definedExternally
    
    /**
     * The texture address mode for U coordinates.
     * Use constants like CLAMP_ADDRESSMODE to control texture wrapping.
     */
    val wrapU: Int? get() = definedExternally
    
    /**
     * The texture address mode for V coordinates.
     * Use constants like CLAMP_ADDRESSMODE to control texture wrapping.
     */
    val wrapV: Int? get() = definedExternally
    
    /**
     * The format of the texture.
     * Determines how the texture data is stored.
     */
    val format: Int? get() = definedExternally
    
    /**
     * The type of the texture.
     * Determines how the texture is processed.
     */
    val type: Int? get() = definedExternally
    
    /**
     * Defines if the texture should be created as a cube texture.
     * Default is false.
     */
    val isCube: Boolean? get() = definedExternally
}

external interface CreateBoxOptions {
    val width: Float? get() = definedExternally
    val height: Float? get() = definedExternally
    val depth: Float? get() = definedExternally
    val updatable: Boolean? get() = definedExternally
}

external interface CreateDiscOptions {
    val radius: Float? get() = definedExternally
    val tessellation: Int? get() = definedExternally
    val updatable: Boolean? get() = definedExternally
}

external interface DynamicTextureOptions {
    val width: Int? get() = definedExternally
    val height: Int? get() = definedExternally
}

external class DynamicTexture(
    name: String,
    options: DynamicTextureOptions,
    scene: Scene,
    generateMipMaps: Boolean = definedExternally,
    samplingMode: Int = definedExternally,
    format: Int = definedExternally
) : Texture {

    fun update(invertY: Boolean = definedExternally)
    fun drawText(
        text: String,
        x: Int,
        y: Int,
        font: String,
        color: String,
        clearColor: String = definedExternally,
        invertY: Boolean = definedExternally,
        update: Boolean = definedExternally
    )
    fun getContext(): CanvasRenderingContext2D
}

/**
 * The GlowLayer Helps adding a glow effect around meshes.
 * Once instantiated in a scene, simply use the pushMesh or removeMesh methods to add or remove
 * glowy meshes to your scene.
 */
external class GlowLayer(name: String, scene: Scene) {
    /**
     * The intensity of the glow
     */
    var intensity: Float

    /**
     * The color of the glow
     */
    var blurKernelSize: Float

    /**
     * Add a mesh to the glow layer
     */
    fun addIncludedOnlyMesh(mesh: AbstractMesh): AbstractMesh

    /**
     * Remove a mesh from the included only meshes
     */
    fun removeIncludedOnlyMesh(mesh: AbstractMesh): AbstractMesh

    /**
     * Dispose the glow layer
     */
    fun dispose()
}

external class GridMaterial(name: String, scene: Scene) : Material {
    var opacityTexture: Texture
    var mainColor: Color3
    var lineColor: Color3
    var opacity: Float
    var gridRatio: Float
    var majorUnitFrequency: Int
    var fogEnabled: Boolean
    var gridOffset: Vector3
    var antialias: Boolean
}

/**
 * Interface for shader material options
 */
external interface ShaderMaterialOptions {
    /**
     * Array of attribute names used in the shader
     */
    var attributes: Array<String>
    
    /**
     * Array of uniform names used in the shader
     */
    var uniforms: Array<String>
    
    /**
     * Array of sampler names used in the shader
     */
    var samplers: Array<String>
    
    /**
     * The vertex shader code
     */
    val vertexSource: String? get() = definedExternally
    
    /**
     * The fragment shader code
     */
    val fragmentSource: String? get() = definedExternally
}

/**
 * The PBRMaterial is the default material used in Physically Based Rendering.
 * This material is the most advanced one in the babylon.js library.
 */
/**
 * ShaderMaterial is used to create custom shaders in BabylonJS
 */
external class ShaderMaterial(name: String, scene: Scene, options: ShaderMaterialOptions) : Material {
    /**
     * Sets a float value on the shader
     * @param name The name of the uniform
     * @param value The float value
     */
    fun setFloat(name: String, value: Float)
    
    /**
     * Sets a Vector2 value on the shader
     * @param name The name of the uniform
     * @param value The Vector2 value
     */
    fun setVector2(name: String, value: Vector2)
    
    /**
     * Sets a Vector3 value on the shader
     * @param name The name of the uniform
     * @param value The Vector3 value
     */
    fun setVector3(name: String, value: Vector3)
    
    /**
     * Sets the vertex shader code
     * @param code The vertex shader code
     */
    fun setVertexShader(code: String)

    /**
     * Sets the fragment shader code
     * @param code The fragment shader code
     */
    fun setFragmentShader(code: String)
}

open external class PBRMaterial(name: String, scene: Scene) : Material {
    var albedoColor: Color3
    var metallic: Float
    var roughness: Float
    var emissiveColor: Color3
    var emissiveIntensity: Float
    var reflectivityColor: Color3
    var albedoTexture: Texture?
    var metallicTexture: Texture?
    var roughnessTexture: Texture?
    var emissiveTexture: Texture?
    var bumpTexture: Texture?
    var lightmapTexture: Texture
    var refractionTexture: Texture
    var ambientTexture: Texture
    var environmentTexture: Texture
    var reflectionTexture: Texture
    var clearCoat: dynamic
    var anisotropy: dynamic
    var brdf: dynamic
    var sheen: dynamic
    var subSurface: dynamic
    var usePhysicalLightFalloff: Boolean
    var useRadianceOverAlpha: Boolean
    var useSpecularOverAlpha: Boolean
    var enableSpecularAntiAliasing: Boolean
    var realTimeFiltering: Boolean
    var realTimeFilteringQuality: Int
    
    /**
     * Observable triggered when the material is bound to a mesh
     */
    val onBindObservable: Observable<Material>
    
    /**
     * Gets the current effect (shader)
     */
    fun getEffect(): Effect?
    
    /**
     * Custom shader name resolver
     * 
     * @param shaderName The name of the shader
     * @param uniforms The list of uniforms
     * @param uniformBuffers The list of uniform buffers
     * @param samplers The list of samplers
     * @param defines The list of defines
     * @param attributes The list of attributes
     * @param options The options to customize the shader
     * @return The resolved shader name or options
     */
    var customShaderNameResolve: ((
        shaderName: String,
        uniforms: dynamic,
        uniformBuffers: dynamic,
        samplers: dynamic,
        defines: dynamic,
        attributes: dynamic,
        options: dynamic
    ) -> dynamic)?
}

/**
 * Class used to load scene from files such as .gltf, .glb, .babylon, etc.
 */
/**
 * Tools class provides utility functions for BabylonJS.
 */
external class Tools {
    companion object {
        /**
         * Creates a screenshot of the scene using the active camera.
         * 
         * @param engine The engine to use for rendering
         * @param camera The camera to use for rendering
         * @param options The screenshot options (width, height)
         * @param onSuccess A callback with the screenshot data URL when successful
         */
        fun CreateScreenshot(
            engine: Engine,
            camera: Camera?,
            options: ScreenshotOptions = definedExternally,
            onSuccess: (String) -> Unit
        )
    }
}

/**
 * Interface for screenshot options.
 */
external interface ScreenshotOptions {
    /**
     * Width of the screenshot
     */
    val width: Int
    
    /**
     * Height of the screenshot
     */
    val height: Int
}

external class SceneLoader {
    companion object {
        /**
         * Import meshes from a file into a scene
         * @param rootUrl a string that defines the root url for the scene and resources or the concatenation of rootURL and filename
         * @param sceneFilename a string that defines the name of the scene file or starts with "data:" following by the stringified version of the scene or a File object (default: empty string)
         * @param scene is the instance of Scene to append to
         * @param onSuccess a callback with a list of imported meshes, particleSystems, and skeletons when import succeeds
         * @param onProgress a callback with a progress event for each file being loaded
         * @param onError a callback with the scene, a message, and possibly an exception when import fails
         * @param pluginExtension the extension used to determine the plugin
         * @returns The loaded plugin
         */
        fun ImportMesh(
            meshNames: String?,
            rootUrl: String,
            sceneFilename: String,
            scene: Scene,
            onSuccess: (Array<AbstractMesh>, Array<dynamic>, Array<dynamic>, Array<dynamic>) -> Unit = definedExternally,
            onProgress: ((event: dynamic) -> Unit)? = definedExternally,
            onError: ((scene: Scene, message: String, exception: dynamic) -> Unit)? = definedExternally,
            pluginExtension: String? = definedExternally
        ): dynamic
        
        /**
         * Load a scene
         * @param rootUrl a string that defines the root url for the scene and resources or the concatenation of rootURL and filename
         * @param sceneFilename a string that defines the name of the scene file or starts with "data:" following by the stringified version of the scene or a File object (default: empty string)
         * @param engine is the instance of Engine to use to create the scene
         * @param onSuccess a callback with the scene when import succeeds
         * @param onProgress a callback with a progress event for each file being loaded
         * @param onError a callback with the scene, a message, and possibly an exception when import fails
         * @param pluginExtension the extension used to determine the plugin
         * @returns The loaded plugin
         */
        fun Load(
            rootUrl: String,
            sceneFilename: String,
            engine: Engine,
            onSuccess: (Scene) -> Unit = definedExternally,
            onProgress: ((event: dynamic) -> Unit)? = definedExternally,
            onError: ((message: String, exception: dynamic) -> Unit)? = definedExternally,
            pluginExtension: String? = definedExternally
        ): dynamic
        
        /**
         * Append a scene
         * @param rootUrl a string that defines the root url for the scene and resources or the concatenation of rootURL and filename
         * @param sceneFilename a string that defines the name of the scene file or starts with "data:" following by the stringified version of the scene or a File object (default: empty string)
         * @param scene is the instance of Scene to append to
         * @param onSuccess a callback with the scene when import succeeds
         * @param onProgress a callback with a progress event for each file being loaded
         * @param onError a callback with the scene, a message, and possibly an exception when import fails
         * @param pluginExtension the extension used to determine the plugin
         * @returns The loaded plugin
         */
        fun Append(
            rootUrl: String,
            sceneFilename: String,
            scene: Scene,
            onSuccess: (Scene) -> Unit = definedExternally,
            onProgress: ((event: dynamic) -> Unit)? = definedExternally,
            onError: ((scene: Scene, message: String, exception: dynamic) -> Unit)? = definedExternally,
            pluginExtension: String? = definedExternally
        ): dynamic
    }
}

/**
 * Interface for EffectWrapper options.
 */
external interface EffectWrapperOptions {
    /**
     * Name of the effect
     */
    val name: String
    
    /**
     * Vertex shader code
     */
    val vertexShader: String
    
    /**
     * Fragment shader code
     */
    val fragmentShader: String
    
    /**
     * Names of the samplers used in the shader
     */
    val samplerNames: Array<String>
    
    /**
     * Names of the uniforms used in the shader
     */
    val uniformNames: Array<String>
    
    /**
     * Whether to use the shader store
     */
    val useShaderStore: Boolean

    val allowEmptySourceTexture: Boolean
    
    /**
     * Attributes used in the shader
     */
    val attributes: Array<String>?
    
    /**
     * Defines used in the shader
     */
    val defines: Array<String>?
    
    /**
     * The engine to use for rendering
     */
    val engine: Engine?
}

/**
 * Wraps an effect to be used for rendering
 */
external class EffectWrapper {
    /**
     * Name of the effect
     */
    var name: String
    
    /**
     * Vertex shader code
     */
    var vertexShader: String
    
    /**
     * Fragment shader code
     */
    var fragmentShader: String
    
    /**
     * Names of the samplers used in the shader
     */
    var samplerNames: Array<String>
    
    /**
     * Names of the uniforms used in the shader
     */
    var uniformNames: Array<String>
    
    /**
     * Whether to use the shader store
     */
    var useShaderStore: Boolean
    
    /**
     * The effect instance
     */
    var effect: Effect
        get() = definedExternally
        set(value) = definedExternally
    
    /**
     * Creates a new EffectWrapper
     * @param options Options to create the effect
     */
    constructor(options: EffectWrapperOptions)
}
