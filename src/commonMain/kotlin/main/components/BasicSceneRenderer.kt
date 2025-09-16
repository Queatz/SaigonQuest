package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import lib.*
import main.components.objects.ObjectAssetManager
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Canvas
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.w3c.dom.HTMLCanvasElement

/**
 * Basic scene renderer using BabylonJS.
 * - 2:1 landscape container
 * - ArcRotateCamera and HemisphericLight
 * - Loads a world GLB when collection is null
 * - Loads a single object collection when collection is provided
 */
@Composable
fun BasicSceneRenderer(
    filename: String,
    collection: String? = null,
) {
    var canvas by remember { mutableStateOf<HTMLCanvasElement?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Outer container maintains 2:1 aspect via CSS aspect-ratio
    Div(attrs = {
        style {
            width(100.percent)
            property("aspect-ratio", "2 / 1")
            position(Position.Relative)
            padding(0.px)
            marginBottom(8.px)
            border(1.px, LineStyle.Solid, Color.gray)
            borderRadius(12.px)
            backgroundColor(rgb(20, 22, 24))
            property("overflow", "hidden")
        }
    }) {
        // Canvas fills container
        Canvas(attrs = {
            style {
                position(Position.Absolute)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.percent)
                display(DisplayStyle.Block)
            }
            ref { el ->
                canvas = el
                onDispose { canvas = null }
            }
        })
        // Loading overlay
        if (isLoading) {
            Div(attrs = {
                style {
                    position(Position.Absolute)
                    top(0.px)
                    left(0.px)
                    width(100.percent)
                    height(100.percent)
                    display(DisplayStyle.Flex)
                    property("align-items", "center")
                    property("justify-content", "center")
                    property("background", "rgba(0,0,0,0.25)")
                    color(Color.white)
                }
            }) {
                P { Translated("Loading...") }
            }
        }
    }

    // Initialize Babylon when canvas is ready
    DisposableEffect(canvas, filename, collection) {
        var engine: Engine? = null
        var scene: Scene? = null
        var resizeListener: ((dynamic) -> Unit)? = null
        var loadedMeshes: Array<AbstractMesh>? = null
        var loadedMeshNode: AbstractMesh? = null
        var loadedTransformNode: TransformNode? = null

        val c = canvas
        if (c != null) {
            engine = Engine(c, true)
            scene = Scene(engine!!)

            val camera = ArcRotateCamera(
                name = "camera",
                alpha = 0f,
                beta = 1.1f,
                radius = if (collection == null) 12f else 3.5f,
                target = Vector3(0f, 0f, 0f),
                scene = scene!!,
            )
            camera.attachControl(true)

            HemisphericLight("hemi", Vector3(0f, 1f, 0f), scene!!)

            // Ensure Babylon loaders are available for GLB/GLTF
            try { initializeBabylonJSLoaders() } catch (_: Throwable) { }

            // Load content
            try {
                if (collection == null) {
                    // World preview: load entire GLB
                    SceneLoader.ImportMesh(
                        meshNames = null,
                        rootUrl = "/raw/data/",
                        sceneFilename = "$filename.glb",
                        scene = scene,
                        onSuccess = { meshes, _, _, _, _ ->
                            loadedMeshes = meshes
                            isLoading = false
                        },
                        onProgress = null,
                        onError = { _, message, _ ->
                            console.warn("[BasicSceneRenderer] World load error: $message")
                            isLoading = false
                        },
                        pluginExtension = null,
                    )
                } else {
                    // Objects preview: load a single collection via ObjectAssetManager
                    val manager = ObjectAssetManager(scene)
                    manager.getNode(
                        file = filename,
                        collection = collection,
                        onSuccess = { node ->
                            when (node) {
                                is AbstractMesh -> {
                                    loadedMeshNode = node
                                    // Ensure at origin
                                    try { node.position = Vector3(0f, 0f, 0f) } catch (_: Throwable) { }
                                }
                                is TransformNode -> {
                                    loadedTransformNode = node
                                    // Parent node defaults to origin
                                }
                            }
                            isLoading = false
                        },
                        onError = { msg ->
                            console.warn("[BasicSceneRenderer] Object collection load error: $msg")
                            isLoading = false
                        },
                    )
                }
            } catch (t: Throwable) {
                console.error("[BasicSceneRenderer] Failed to load asset preview", t)
            }

            engine!!.runRenderLoop {
                scene!!.render()
            }

            resizeListener = { engine?.resize() }
            window.addEventListener("resize", resizeListener!!)
        }

        onDispose {
            if (resizeListener != null) {
                window.removeEventListener("resize", resizeListener!!)
            }
            try {
                // Dispose any loaded nodes/meshes explicitly
                loadedMeshes?.forEach { m ->
                    try { m.dispose() } catch (_: Throwable) { }
                }
                loadedMeshNode?.let { try { it.dispose() } catch (_: Throwable) { } }
                loadedTransformNode?.let { try { it.dispose() } catch (_: Throwable) { } }
            } catch (_: Throwable) { }
            try { scene?.dispose() } catch (_: Throwable) { }
            try { engine?.dispose() } catch (_: Throwable) { }
        }
    }
}
