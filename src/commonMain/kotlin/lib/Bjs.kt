@file:JsModule("@babylonjs/core")
@file:JsNonModule

package lib

import org.khronos.webgl.ArrayBufferView
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.Promise

/**
 * Interface for a node in the scene graph
 */
open external class Node {
    /**
     * The name of the node
     */
    open var name: String
    
    /**
     * The parent node
     */
    open var parent: Node?
    
    /**
     * Position of the node in local space
     */
    open var position: Vector3
    
    /**
     * Rotation of the node in local space
     */
    open var rotation: Vector3
    
    /**
     * Scaling of the node in local space
     */
    open var scaling: Vector3

    /**
     * List of animations attached to the node
     */
    open val animations: Array<dynamic>

    open var metadata: MeshMetadata?

    open fun getAbsolutePosition(): Vector3 = definedExternally

    /**
     * Gets all child nodes of this node
     *
     * @param predicate Optional predicate to filter nodes
     * @param directDescendantsOnly If true, only direct descendants will be returned
     * @return Array of child nodes
     */
    open fun getChildren(
        predicate: ((node: Node) -> Boolean)? = definedExternally,
        directDescendantsOnly: Boolean? = definedExternally
    ): Array<Node>

    /**
     * Gets all child meshes of this node
     * 
     * @param directDescendantsOnly If true, only direct descendants will be returned
     * @return Array of child meshes
     */
    open fun getChildMeshes(directDescendantsOnly: Boolean? = definedExternally): Array<AbstractMesh>

    /**
     * Set the enabled state of the node
     *
     * @param value The enabled state
     */
    open fun setEnabled(value: Boolean)

    /**
     * Check if the node is enabled
     *
     * @return True if enabled, false otherwise
     */
    open fun isEnabled(): Boolean

    /**
     * Disposes of the node and all its resources
     */
    open fun dispose()
}

/**
 * A transform node is a node that can be used to transform other nodes in the scene
 * without rendering anything itself. It's useful as a container for other nodes.
 */
open external class TransformNode(name: String, scene: Scene? = definedExternally) : Node {
    override var name: String
    override var parent: Node?
    override var position: Vector3
    override var rotation: Vector3
    override var scaling: Vector3
    open var rotationQuaternion: Quaternion?

    override var metadata: MeshMetadata?

    open fun getWorldMatrix(): Matrix
    open fun computeWorldMatrix(force: Boolean = definedExternally): Matrix
    
    /**
     * Gets all child meshes of this node
     * 
     * @param directDescendantsOnly If true, only direct descendants will be returned
     * @return Array of child meshes
     */
    override fun getChildMeshes(directDescendantsOnly: Boolean?): Array<AbstractMesh>
    
    /**
     * Disposes of the transform node and all its resources
     */
    override fun dispose()
}

/**
 * Interface for mesh metadata
 */
external interface MeshMetadata {
    /**
     * Original material of the mesh (used for restoring after effects)
     */
    var originalMaterial: Material?

    var isItem: Boolean?

    var gltf: Gltf?
}

external interface Gltf {
    var extras: dynamic
}

abstract external class AbstractMesh : TransformNode {
    var isVisible: Boolean
    var isPickable: Boolean
    override var position: Vector3
    var material: Material
    var receiveShadows: Boolean
    override var rotation: Vector3
    override var rotationQuaternion: Quaternion?
    var origin: Vector3
    override var scaling: Vector3
    var billboardMode: Int
    val onAfterWorldMatrixUpdateObservable: Observable<AbstractMesh>
    var instancedBuffers: dynamic
    var actionManager: ActionManager?
    override var name: String
    override var parent: Node?
    var infiniteDistance: Boolean
    var checkCollisions: Boolean
    var renderingGroupId: Int

    override fun getWorldMatrix(): Matrix

    /**
     * Tests if a ray intersects the mesh
     * @param ray The ray to test
     * @param fastCheck Defines if the first intersection will be used (and not the closest)
     * @return Information about the intersection point
     */
    fun intersects(ray: Ray, fastCheck: Boolean = definedExternally): PickingInfo
    
    fun getVerticesData(kind: String): Array<Float>?
    fun setVerticesData(kind: String, data: Array<Float>, updatable: Boolean)
    fun clone(name: String): AbstractMesh
    fun registerInstancedBuffer(name: String, size: Int)
    fun addRotation(x: Float, y: Float, z: Float)
    override fun dispose()
    
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
) : ShadowGenerator {
    var lambda: Float
    var bias: Float
    var normalBias: Float
    var transparencyShadow: Boolean
    var stabilizeCascades: Boolean
    var shadowMaxZ: Float
    var autoCalcDepthBounds: Boolean
    var numCascades: Int
    var cascadeBlendPercentage: Float

    fun splitFrustum()
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

external open class Light : Node {
    open var intensity: Float
    open var diffuse: Color3
    open var includedOnlyMeshes: Array<AbstractMesh>
    open var excludedMeshes: Array<AbstractMesh>
    open fun getTypeID(): Int
    override fun dispose()
}

external interface IShadowLight {
    var intensity: Float
    var diffuse: Color3
    var shadowMinZ: Float
    var shadowMaxZ: Float
    var position: Vector3
    var direction: Vector3
    var includedOnlyMeshes: Array<AbstractMesh>
    var excludedMeshes: Array<AbstractMesh>
    fun getTypeID(): Int
    fun dispose()
}

external class DirectionalLight(name: String, direction: Vector3, scene: Scene) : Light, IShadowLight {
    override var intensity: Float
    override var diffuse: Color3
    override var shadowMinZ: Float
    override var shadowMaxZ: Float
    override var position: Vector3
    override var direction: Vector3
    override var includedOnlyMeshes: Array<AbstractMesh>
    override var excludedMeshes: Array<AbstractMesh>
    override fun getTypeID(): Int
    override fun dispose()
}

external class PointLight(name: String, position: Vector3, scene: Scene) : Light, IShadowLight {
    override var intensity: Float
    override var diffuse: Color3
    override var shadowMinZ: Float
    override var shadowMaxZ: Float
    override var position: Vector3
    override var direction: Vector3
    override var includedOnlyMeshes: Array<AbstractMesh>
    override var excludedMeshes: Array<AbstractMesh>
    override fun getTypeID(): Int
    override fun dispose()
}

external class HemisphericLight(name: String, direction: Vector3, scene: Scene) : Light {
    override var diffuse: Color3
    var groundColor: Color3
    override var intensity: Float
    override var includedOnlyMeshes: Array<AbstractMesh>
    override var excludedMeshes: Array<AbstractMesh>
    override fun getTypeID(): Int
    override fun dispose()
}

external class Scene(engine: Engine? = definedExternally) {
    var fogMode: Int
    var fogDensity: Float
    var fogColor: Color3
    var clearColor: Color4
    var autoClear: Boolean
    var ambientColor: Color3
    var activeCamera: Camera?
    var pointerX: Int
    var pointerY: Int
    var deltaTime: Float
    val onKeyboardObservable: Observable<KeyboardInfo>
    val onPointerObservable: Observable<PointerInfo>
    
    var imageProcessingConfiguration: ImageProcessingConfiguration
    
    var useClusteredForwardLighting: Boolean
    var useClusteredShadows: Boolean

    /**
     * List of all meshes in the scene
     */
    val meshes: Array<AbstractMesh>

    /**
     * List of all lights in the scene
     */
    val lights: Array<Light>

    /**
     * List of all cameras in the scene
     */
    val cameras: Array<Camera>

    /**
     * List of all animation groups in the scene
     */
    val animationGroups: Array<AnimationGroup>

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

    fun getProjectionMatrix(): Matrix

    fun getViewMatrix(): Matrix

    /**
     * Gets a node by name
     * @param name The name of the node to find
     * @return The node with the given name, or null if not found
     */
    fun getNodeByName(name: String): Node?
    
    fun enableDepthRenderer(
        camera: Camera? = definedExternally,
        storeNonLinearDepth: Boolean = definedExternally,
        force32bitsFloat: Boolean = definedExternally,
    ): DepthRenderer
    fun disableDepthRenderer()
    fun render()
    fun dispose()
    fun createPickingRay(x: Int, y: Int, world: Matrix, camera: Camera? = definedExternally): Ray
    fun registerBeforeRender(callback: () -> Unit)
    fun unregisterBeforeRender(callback: () -> Unit)
    fun getFrameId(): Int
    fun removeMesh(mesh: AbstractMesh)

    /**
     * Sets the rendering auto clear depth stencil state for a rendering group
     * @param renderingGroupId The rendering group id
     * @param autoClearDepthStencil Whether to auto clear depth stencil
     * @param depth Whether to clear depth
     * @param stencil Whether to clear stencil
     */
    fun setRenderingAutoClearDepthStencil(
        renderingGroupId: Int,
        autoClearDepthStencil: Boolean,
        depth: Boolean = definedExternally,
        stencil: Boolean = definedExternally
    )


    /**
     * Adds an animation group to the scene
     * @param animationGroup The animation group to add
     */
    fun addAnimationGroup(animationGroup: AnimationGroup)

    /**
     * Starts an animation on a target
     * @param target The target to animate
     * @param from The start frame
     * @param to The end frame
     * @param loop If true, the animation will loop
     * @param speedRatio The speed ratio
     * @param onAnimationEnd Callback when the animation ends
     * @return The animatable
     */
    fun beginAnimation(
        target: Any, 
        from: Float, 
        to: Float, 
        loop: Boolean = definedExternally, 
        speedRatio: Float = definedExternally, 
        onAnimationEnd: (() -> Unit)? = definedExternally
    ): dynamic

    /**
     * Stops all animations on a target
     * @param target The target to stop animations on
     * @param animationName Optional animation name to stop
     */
    fun stopAnimation(target: Any, animationName: String? = definedExternally)

    companion object {
        val FOGMODE_EXP2: Int
    }
}
external class Mesh(name: String = definedExternally, scene: Scene = definedExternally) : AbstractMesh {
    fun createInstance(name: String): Mesh

    override fun getChildMeshes(directDescendantsOnly: Boolean?): Array<AbstractMesh>

    override var metadata: MeshMetadata?

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

    override var metadata: MeshMetadata?

    override fun getChildMeshes(directDescendantsOnly: Boolean?): Array<AbstractMesh>

}

external class MeshBuilder {
    companion object {
        fun CreateGround(name: String, options: CreateGroundOptions = definedExternally, scene: Scene = definedExternally): Mesh
        fun CreatePlane(name: String, options: CreatePlaneOptions = definedExternally, scene: Scene = definedExternally): Mesh
        fun CreateSphere(name: String, options: CreateSphereOptions = definedExternally, scene: Scene = definedExternally): Mesh
        fun CreateIcoSphere(name: String, options: CreateIcoSphereOptions = definedExternally, scene: Scene = definedExternally): Mesh
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
    var forceDepthWrite: Boolean

    var disableColorWrite: Boolean
    var disableDepthWrite: Boolean
    var alpha: Float
    var id: String
    var name: String
    var fogEnabled: Boolean
    var disableLighting: Boolean

    fun getEffect(): Effect?

    fun clone(name: String): Material
    fun dispose(forceDisposeEffect: Boolean = definedExternally)
    fun isReady(): Boolean

    /**
     * Observable triggered when the material is bound to a mesh
     */
    val onBindObservable: Observable<AbstractMesh>

    companion object {
        val MATERIAL_ALPHATEST: Int
        val MATERIAL_OPAQUE: Int
        val MATERIAL_ALPHABLEND: Int
        val MATERIAL_ALPHATESTANDBLEND: Int
    }
}



open external class StandardMaterial(name: String, scene: Scene) : Material {
    var diffuseTexture: Texture?
    var emissiveTexture: Texture?
    var opacityTexture: Texture?
    var diffuseColor: Color3
    var specularColor: Color3
    var emissiveColor: Color3
    var roughness: Float
    var useAlphaFromDiffuseTexture: Boolean
}

abstract external class BaseTexture {
    var gammaSpace: Boolean
    var textureFormat: Int
    var coordinatesMode: Int
}

/**
 * Represents a texture in the scene.
 * 
 * @param url The URL of the texture
 * @param scene The scene the texture belongs to
 * @param options Options to configure the texture
 */
open external class Texture(url: String, scene: Scene, options: TextureOptions = definedExternally) : BaseTexture {
    var hasAlpha: Boolean
    var wrapU: Int
    var wrapV: Int
    var url: String
    var uOffset: Float
    var vOffset: Float
    var uScale: Float
    var vScale: Float
    var samplingMode: Int
    val onLoadObservable: Observable<Texture>

    fun clone(): Texture
    fun dispose()

    /**
     * Gets the size of the texture
     * @return The size as an object with width and height
     */
    fun getSize(): TextureSize = definedExternally

    /**
    * Reads pixels from this texture if it is a render target (e.g., DepthRenderer's depth map).
     * Returns a Promise that resolves to a typed array (Float32Array or Uint8Array).
     */
    fun readPixels(
        faceIndex: Int? = definedExternally,
        level: Int? = definedExternally,
        buffer: ArrayBufferView? = definedExternally,
        flushRenderer: Boolean? = definedExternally,
        noDataConversion: Boolean? = definedExternally,
        x: Int? = definedExternally,
        y: Int? = definedExternally,
        width: Int? = definedExternally,
        height: Int? = definedExternally,
    ): Promise<ArrayBufferView>?

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
        val EQUIRECTANGULAR_MODE: Int
    }
}

// Subset of RenderTargetTexture we need for depth picking render list control
external interface RenderTargetTexture {
    var renderList: Array<AbstractMesh>?
    fun getSize(): TextureSize
    fun readPixels(
        faceIndex: Int? = definedExternally,
        level: Int? = definedExternally,
        buffer: ArrayBufferView? = definedExternally,
        flushRenderer: Boolean? = definedExternally,
        noDataConversion: Boolean? = definedExternally,
        x: Int? = definedExternally,
        y: Int? = definedExternally,
        width: Int? = definedExternally,
        height: Int? = definedExternally,
    ): Promise<ArrayBufferView>?
}

external class Engine(
    canvas: HTMLCanvasElement = definedExternally,
    antialias: Boolean = definedExternally,
    options: EngineOptions = definedExternally,
    adaptToDeviceRatio: Boolean = definedExternally
) {
    fun runRenderLoop(callback: () -> Unit)
    fun setHardwareScalingLevel(scalingLevel: Int)
    fun resize()
    fun dispose()
    fun getRenderWidth(): Int
    fun getRenderHeight(): Int
    fun getRenderingCanvas(): HTMLCanvasElement?

    var maxFPS: Int

    companion object {
        val TEXTUREFORMAT_ALPHA: Int
        
    }
}

external class Observable<T> {
    fun add(callback: (T) -> Unit)
    fun addOnce(callback: (T) -> Unit)
}

open external class ShadowGenerator(mapSize: Int, light: IShadowLight) {
    fun addShadowCaster(mesh: AbstractMesh)
    fun removeShadowCaster(mesh: AbstractMesh)
    var useBlurCloseExponentialShadowMap: Boolean
    var useExponentialShadowMap: Boolean
    var useBlurExponentialShadowMap: Boolean
    var useKernelBlur: Boolean
    var blurKernel: Int
    var useClusteredShadows: Boolean
    var usePercentageCloserFiltering: Boolean
    var filteringQuality: Int
    var useContactHardeningShadow: Boolean
    var contactHardeningLightSizeUVRatio: Float
    var usePoissonSampling: Boolean
    var darkness: Float
    fun dispose()
    
    companion object {
        val FILTERINGQUALITY_LOW: Int
        val FILTERINGQUALITY_MEDIUM: Int
        val FILTERINGQUALITY_HIGH: Int
    }
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
     * The bounding box derived from minimum and maximum vectors
     */
    val boundingBox: BoundingBox

    /**
     * Updates the bounding info with the given world matrix
     *
     * @param world The world matrix
     */
    fun update(world: Matrix)

    /**
     * Checks if a point is inside the bounding box
     *
     * @param point The point to check
     * @return True if the point is inside the bounding box, false otherwise
     */
    fun isInside(point: Vector3): Boolean
}

/**
 * Class representing an axis-aligned bounding box in 3D space
 */
external class BoundingBox {
    /**
     * The minimum vector of the bounding box in local space
     */
    val minimum: Vector3

    /**
     * The maximum vector of the bounding box in local space
     */
    val maximum: Vector3

    /**
     * The minimum vector of the bounding box in world space
     */
    val minimumWorld: Vector3

    /**
     * The maximum vector of the bounding box in world space
     */
    val maximumWorld: Vector3

    /**
     * Creates a new bounding box
     *
     * @param min The minimum vector
     * @param max The maximum vector
     */
    constructor(min: Vector3, max: Vector3)

    /**
     * Tests if a point is inside the bounding box
     *
     * @param point The point to test
     * @return True if the point is inside the box, false otherwise
     */
    fun intersectsPoint(point: Vector3): Boolean

    /**
     * Tests if the bounding box intersects with another bounding box
     *
     * @param boundingBox The other bounding box to test
     * @return True if the boxes intersect, false otherwise
     */
    fun intersectsBoundingBox(boundingBox: BoundingBox): Boolean
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
        fun Project(
            vector: Vector3,
            world: Matrix,
            transform: Matrix,
            viewport: Viewport,
        ): Vector3
        /**
         * Unprojects a screen-space point (x,y in pixels, z in [0,1]) to world space.
         * @param source The screen-space vector where x,y are pixels and z is depth in [0,1]
         * @param viewportWidth Width of the viewport in pixels
         * @param viewportHeight Height of the viewport in pixels
         * @param world The world matrix (usually Matrix.Identity())
         * @param view The view matrix
         * @param projection The projection matrix
         * @return The unprojected world space position
         */
        fun Unproject(
            source: Vector3,
            viewportWidth: Int,
            viewportHeight: Int,
            world: Matrix,
            view: Matrix,
            projection: Matrix,
        ): Vector3
    }
}

external class Matrix {
    var m: org.khronos.webgl.Float32Array
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

abstract external class Camera : Node {
    fun getScene(): Scene
    var minZ: Float
    var maxZ: Float
    override var position: Vector3
    var fov: Float
    var globalPosition: Vector3
    var upVector: Vector3
    var viewport: Viewport
    val onProjectionMatrixChangedObservable: Observable<Camera>

    fun getDirection(direction: Vector3): Vector3
    fun getForwardRay(length: Float = definedExternally): Ray
    fun getProjectionMatrix(usingRightHandedSystem: Boolean = definedExternally): Matrix
    fun freezeProjectionMatrix()
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
    var targetScreenOffset: Vector2
    var inputs: ArcRotateCameraInputs

    fun attachControl(noPreventDefault: Boolean = definedExternally)
    fun detachControl()
    fun getViewMatrix(): Matrix
    fun setTarget(target: Vector3)
    fun setPosition(position: Vector3)
}

external class ArcRotateCameraInputs {
    val removeByType: (String) -> Unit
    
    /**
     * Adds a new input to the camera
     * 
     * @param input The input to add
     */
    fun add(input: Any)
}

external class ArcRotateCameraKeyboardMoveInput {
    var angularSpeed: Float
}

/**
 * Mouse wheel input for ArcRotateCamera
 */
external class ArcRotateCameraMouseWheelInput {
    var wheelPrecision: Float
}

/**
 * Pointers (mouse/touch) input for ArcRotateCamera
 */
external class ArcRotateCameraPointersInput {
    var angularSensibilityX: Float
    var angularSensibilityY: Float
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
    val button: Short
    val pointerType: String
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
    var length: Double

    /**
     * Tests if the ray intersects a mesh
     * This is a wrapper around the mesh.intersects method
     * 
     * @param mesh The mesh to test
     * @param fastCheck Defines if the first intersection will be used (and not the closest)
     * @return Information about the intersection point
     */
    fun intersectsMesh(mesh: AbstractMesh, fastCheck: Boolean = definedExternally): PickingInfo

    /**
     * Tests if the ray intersects a list of meshes
     * @param meshes Array of meshes to test
     * @param fastCheck Defines if the first intersection will be used (and not the closest)
     * @param results Optional array to store the picking info results
     * @return Array of picking info results
     */
    fun intersectsMeshes(
        meshes: Array<AbstractMesh>,
        fastCheck: Boolean = definedExternally,
        results: Array<PickingInfo>? = definedExternally,
    ): Array<PickingInfo>


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
): PostProcessRenderPipeline {
    var depthOfFieldEnabled: Boolean
    var depthOfField: DepthOfFieldEffect
    var depthOfFieldBlurLevel: Int
    var bloomEnabled: Boolean
    var bloomThreshold: Float
    var bloomWeight: Float
    var bloomKernel: Float
    var bloomScale: Float
    var grainEnabled: Boolean
    var sharpenEnabled: Boolean
    var chromaticAberrationEnabled: Boolean
    /**
     * The grain effect configuration object
     */
    var grain: GrainEffect
    /**
     * Enables image processing
     */
    var imageProcessingEnabled: Boolean
    /**
     * The image processing configuration
     */
    var imageProcessing: ImageProcessingPostProcess
    /**
     * The sharpen effect configuration
     */
    var sharpen: SharpenEffect



    /**
     * Enables fog rendering in the pipeline
     */
    var fogEnabled: Boolean

    /**
     * The chromatic aberration effect configuration
     */
    var chromaticAberration: ChromaticAberrationEffect

}

/**
 * Interface for controlling chromatic aberration effect parameters
 */
external interface ChromaticAberrationEffect {
    /**
     * Controls the amount of chromatic aberration (0.0-1.0)
     */
    var aberrationAmount: Float

    /**
     * Controls the radial intensity of the effect (0.0-1.0)
     */
    var radialIntensity: Float
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

    var contrast: Float

    var exposure: Float

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
     * The clear color to use when clearing the render target
     */
    var clearColor: Color4

    /**
     * Whether to automatically clear the render target before applying the effect
     */
    var autoClear: Boolean

    /**
     * Whether the color inputs are in linear space
     */
    var fromLinearSpace: Boolean

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
    /**
     * Enables tone mapping
     */
    var toneMappingEnabled: Boolean

    var gamma: Float

    /**
     * Gets whether the effects should be applied through a post process
     */
    var applyByPostProcess: Boolean

    /**
     * Gets whether the image processing is enabled
     */
    var isEnabled: Boolean
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
 * Interface for controlling the sharpen effect parameters
 */
external interface SharpenEffect {
    /**
     * Controls the amount of edge enhancement (0.0-1.0)
     */
    var edgeAmount: Float

    /**
     * Controls the amount of color enhancement (0.0-1.0)
     */
    var colorAmount: Float
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
    fun getDepthMap(): RenderTargetTexture
    fun getCamera(): Camera
    fun setMaterialForRendering(mesh: AbstractMesh, material: Material?)

    var forceDepthWriteTransparentMeshes: Boolean
    
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

    fun setBool(name: String, bool: Boolean)

    val vertexShaderSource: String

    /**
     * Gets the fragment shader source code
     * @return The fragment shader source code as a string
     */
    val fragmentShaderSource: String

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

open external class PostProcessRenderPipeline {
    fun addCamera(camera: Camera)
    fun removeCamera(camera: Camera)
    fun dispose(): Unit
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
): PostProcessRenderPipeline {
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

external interface CreateIcoSphereOptions {
    val radius: Float? get() = definedExternally
    val subdivisions: Int? get() = definedExternally
    val flat: Boolean? get() = definedExternally
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

// Strongly-typed options for MeshBuilder.CreateTube to avoid using `dynamic`
external interface CreateTubeOptions {
    // Ordered list of points describing the center path of the tube
    val path: Array<Vector3>? get() = definedExternally
    // Constant radius of the tube along the path
    val radius: Float? get() = definedExternally
    // Number of radial segments
    val tessellation: Int? get() = definedExternally
    // Whether the geometry is updatable for in-place updates
    val updatable: Boolean? get() = definedExternally
    // When provided, updates the given instance instead of creating a new mesh
    val instance: Mesh? get() = definedExternally
}

external interface DynamicTextureOptions {
    val width: Int? get() = definedExternally
    val height: Int? get() = definedExternally
}

external interface TextureSize {
    val width: Int
    val height: Int
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

/**
 * The HighlightLayer is used to highlight meshes with a specific color.
 * Once instantiated in a scene, simply use the addMesh or removeMesh methods to add or remove
 * highlighted meshes.
 */
external class HighlightLayer(name: String, scene: Scene) {
    /**
     * The intensity of the highlight
     */
    var intensity: Float
    
    /**
     * Blur horizontal size for the highlight
     */
    var blurHorizontalSize: Float
    
    /**
     * Blur vertical size for the highlight
     */
    var blurVerticalSize: Float

    /**
     * Controls the visibility of the outer glow effect
     */
    var outerGlow: Boolean

    /**
     * Controls the visibility of the inner glow effect
     */
    var innerGlow: Boolean

    /**
     * Main texture ratio for the highlight
     */
    var mainTextureRatio: Float

    /**
    * Add a mesh to the highlight layer with a specific color
     * 
     * @param mesh The mesh to highlight
     * @param color The color to use for the highlight
     * @param glowEmissiveOnly If true, the highlight will only affect the emissive color of the mesh
     * @return The highlighted mesh
     */
    fun addMesh(mesh: AbstractMesh, color: Color3, glowEmissiveOnly: Boolean = definedExternally): AbstractMesh
    
    /**
     * Remove a mesh from the highlight layer
     * 
     * @param mesh The mesh to remove from the highlight
     * @return The removed mesh
     */
    fun removeMesh(mesh: AbstractMesh): AbstractMesh

    /**
     * Exclude a mesh from the highlight layer
     * 
     * @param mesh The mesh to exclude
     */
    fun addExcludedMesh(mesh: AbstractMesh)

    /**
     * Remove an excluded mesh from the highlight layer
     * 
     * @param mesh The mesh to remove from the exclusion
     */
    fun removeExcludedMesh(mesh: AbstractMesh)
    
    /**
     * Dispose the highlight layer
     */
    fun dispose()
}

/**
 * The SelectionOutlineLayer is used to draw an outline around meshes.
 */
external class SelectionOutlineLayer(name: String, scene: Scene) {
    /**
     * Color of the outline
     */
    var outlineColor: Color3
    
    /**
     * Width of the outline
     */
    var outlineThickness: Float

    /**
     * Main texture ratio for the outline
     */
    var mainTextureRatio: Float

    /**
    * Add a mesh to the selection outline layer
     * 
     * @param mesh The mesh to outline
     */
    fun addSelection(mesh: AbstractMesh)
    
    /**
     * Clear all selected meshes from the selection outline layer
     */
    fun clearSelection()

    /**
     * Dispose the selection outline layer
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
    var gridOffset: Vector3
    var antialias: Boolean
}

/**
 * Interface for shader material options
 */
external interface IShaderMaterialOptions {
    // Optional fields are nullable to satisfy Kotlin external interface requirements when providing default JS-backed accessors
    var attributes: Array<String>? get() = definedExternally; set(value) = definedExternally
    var defines: Array<String>? get() = definedExternally; set(value) = definedExternally
    var externalTextures: Array<String>? get() = definedExternally; set(value) = definedExternally
    var extraInitializationsAsync: (() -> Promise<Unit>)? get() = definedExternally; set(value) = definedExternally
    var needAlphaBlending: Boolean? get() = definedExternally; set(value) = definedExternally
    var needAlphaTesting: Boolean? get() = definedExternally; set(value) = definedExternally
    var samplerObjects: Array<String>? get() = definedExternally; set(value) = definedExternally
    var samplers: Array<String>? get() = definedExternally; set(value) = definedExternally
    var shaderLanguage: dynamic /* ShaderLanguage? */ get() = definedExternally; set(value) = definedExternally
    var storageBuffers: Array<String>? get() = definedExternally; set(value) = definedExternally
    var uniformBuffers: Array<String>? get() = definedExternally; set(value) = definedExternally
    var uniforms: Array<String>? get() = definedExternally; set(value) = definedExternally
    var useClipPlane: Boolean? get() = definedExternally; set(value) = definedExternally
}

external interface IShaderPath {
    var fragment: String? get() = definedExternally; set(value) = definedExternally
    var fragmentElement: String? get() = definedExternally; set(value) = definedExternally
    var fragmentSource: String? get() = definedExternally; set(value) = definedExternally
    var spectorName: String? get() = definedExternally; set(value) = definedExternally
    var vertex: String? get() = definedExternally; set(value) = definedExternally
    var vertexElement: String? get() = definedExternally; set(value) = definedExternally
    var vertexSource: String? get() = definedExternally; set(value) = definedExternally
}

/**
 * The PBRMaterial is the default material used in Physically Based Rendering.
 * This material is the most advanced one in the babylon.js library.
 */
/**
 * ShaderMaterial is used to create custom shaders in BabylonJS
 */
external class ShaderMaterial(
    name: String,
    scene: Scene,
    shaderPath: dynamic /* String | IShaderPath */,
    options: IShaderMaterialOptions? = definedExternally,
    storeEffectOnSubMeshes: Boolean? = definedExternally
) : Material {
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
    var useAlphaFromAlbedoTexture: Boolean
    var realTimeFiltering: Boolean
    var realTimeFilteringQuality: Int
    var unlit: Boolean

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
    var customShaderNameResolve: (
        shaderName: String,
        uniforms: Array<String>,
        uniformBuffers: Array<String>,
        samplers: Array<String>,
        defines: Array<String>,
        attributes: Array<String>,
        options: dynamic /* ICustomShaderNameResolveOptions? */
    ) -> String
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

/**
 * Container for assets loaded from a file.
 * Provides access to meshes, transform nodes, and other scene elements.
 */
external class AssetContainer(scene: Scene) {
    /**
     * List of meshes in the asset container
     */
    val meshes: Array<AbstractMesh>
    
    /**
     * List of transform nodes in the asset container
     */
    val transformNodes: Array<Node>
    
    /**
     * List of root nodes in the asset container
     */
    val rootNodes: Array<Node>

    fun getNodes(): Array<Node>
    
    /**
     * Adds all assets from the container to the scene
     */
    fun addAllToScene()

    /**
     * List of animation groups in the asset container
     */
    val animationGroups: Array<AnimationGroup>

    /**
     * Creates a model instance from the asset container
     * @param nameFunction Function to generate names for cloned objects
     * @param cloneMaterials Whether to clone materials
     * @return Object containing the root nodes and meshes of the instantiated model
     */
    fun instantiateModelsToScene(
        nameFunction: (String) -> String = definedExternally, 
        cloneMaterials: Boolean = definedExternally
    ): dynamic
}

external class AnimationGroup(name: String, scene: Scene? = definedExternally) {
    val name: String
    val isPlaying: Boolean
    fun play(loop: Boolean = definedExternally)
    fun stop()
    fun pause()
    fun restart()
}

external class Animation(
    name: String,
    targetProperty: String,
    framePerSecond: Int,
    dataType: Int,
    loopMode: Int = definedExternally,
    enableBlending: Boolean = definedExternally
) {
    fun setKeys(keys: Array<dynamic>)
    fun setEasingFunction(easingFunction: EasingFunction)

    companion object {
        val ANIMATIONTYPE_FLOAT: Int
        val ANIMATIONTYPE_VECTOR3: Int
        val ANIMATIONTYPE_QUATERNION: Int
        val ANIMATIONTYPE_MATRIX: Int
        val ANIMATIONTYPE_COLOR3: Int
        val ANIMATIONTYPE_COLOR4: Int
        val ANIMATIONTYPE_SIZE: Int

        val ANIMATIONLOOPMODE_RELATIVE: Int
        val ANIMATIONLOOPMODE_CYCLE: Int
        val ANIMATIONLOOPMODE_CONSTANT: Int

        fun CreateAndStartAnimation(
            name: String,
            target: Any,
            targetProperty: String,
            framePerSecond: Int,
            totalFrame: Int,
            from: Any,
            to: Any,
            loopMode: Int = definedExternally,
            easingFunction: EasingFunction? = definedExternally,
            onAnimationEnd: (() -> Unit)? = definedExternally
        ): dynamic
    }
}

abstract external class EasingFunction {
    fun setEasingMode(easingMode: Int)
    fun getEasingMode(): Int

    companion object {
        val EASINGMODE_EASEIN: Int
        val EASINGMODE_EASEOUT: Int
        val EASINGMODE_EASEINOUT: Int
    }
}

external class BounceEase(bounces: Int = definedExternally, bounciness: Float = definedExternally) : EasingFunction
external class QuadraticEase : EasingFunction
external class CubicEase : EasingFunction

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
            onSuccess: (Array<AbstractMesh>, Array<dynamic>, Array<dynamic>, Array<dynamic>, transformNodes: Array<Node>) -> Unit = definedExternally,
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
        
        /**
         * Load an asset container
         * @param rootUrl a string that defines the root url for the scene and resources
         * @param sceneFilename a string that defines the name of the scene file
         * @param scene is the instance of Scene to append to
         * @param onProgress a callback with a progress event for each file being loaded
         * @param pluginExtension the extension used to determine the plugin
         * @returns A Promise that resolves with the loaded AssetContainer
         */
        fun LoadAssetContainerAsync(
            rootUrl: String,
            sceneFilename: String,
            scene: Scene,
            onProgress: ((event: dynamic) -> Unit)? = definedExternally,
            pluginExtension: String? = definedExternally
        ): Promise<AssetContainer>
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


// Engine context options for creation (typed wrapper instead of dynamic)
external interface EngineOptions {
    var alpha: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var preserveDrawingBuffer: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var stencil: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var disableWebGL2Support: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    // Simulation/lockstep options
    var deterministicLockstep: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var lockstepMaxSteps: Int?
        get() = definedExternally
        set(value) = definedExternally
    var timeStep: Float?
        get() = definedExternally
        set(value) = definedExternally
}
