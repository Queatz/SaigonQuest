@file:JsModule("@babylonjs/core")
@file:JsNonModule

package lib

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.KeyboardEvent

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

    fun getVerticesData(kind: String): Array<Float>?
    fun setVerticesData(kind: String, data: Array<Float>, updatable: Boolean)
    fun clone(name: String): AbstractMesh
    fun registerInstancedBuffer(name: String, size: Int)
    fun addRotation(x: Float, y: Float, z: Float)
    fun dispose()

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
        fun CreateGround(name: String, options: dynamic = definedExternally, scene: Scene = definedExternally): Mesh
        fun CreatePlane(name: String, options: dynamic = definedExternally, scene: Scene = definedExternally): Mesh
        fun CreateSphere(name: String, options: dynamic = definedExternally, scene: Scene = definedExternally): Mesh
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

open external class Texture(url: String, scene: Scene, options: dynamic = definedExternally) {
    var hasAlpha: Boolean
    var wrapU: Int
    var wrapV: Int
    var url: String

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

abstract external class Camera {
    var minZ: Float
    var maxZ: Float
    var position: Vector3
    var fov: Float
    var globalPosition: Vector3

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
}

external class DepthOfFieldEffect {
    var fStop: Float
    var focalLength: Float
    var focusDistance: Float
}

external class ImageProcessingPostProcess {
    var edgeAmount: Float
    var colorAmount: Float
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

external interface TextureOptions {
    val samplingMode: Int? get() = definedExternally
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
 * The PBRMaterial is the default material used in Physically Based Rendering.
 * This material is the most advanced one in the babylon.js library.
 */
external class PBRMaterial(name: String, scene: Scene) : Material {
    var albedoColor: Color3
    var metallic: Float
    var roughness: Float
    var emissiveColor: Color3
    var emissiveIntensity: Float
    var reflectivityColor: Color3
    var albedoTexture: Texture
    var metallicTexture: Texture
    var roughnessTexture: Texture
    var emissiveTexture: Texture
    var bumpTexture: Texture
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
}

/**
 * Class used to load scene from files such as .gltf, .glb, .babylon, etc.
 */
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
