package main.components.world

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import lib.ArcRotateCamera
import lib.Color3
import lib.CreateSphereOptions
import lib.DirectionalLight
import lib.Engine
import lib.EngineOptions
import lib.Mesh
import lib.MeshBuilder
import lib.Matrix
import lib.Scene
import lib.StandardMaterial
import lib.Texture
import lib.Vector3
import lib.Color4
import lib.PBRMaterial
import lib.TextureOptions
import lib.CreateTubeOptions
import lib.DefaultRenderingPipeline
import org.jetbrains.compose.web.dom.Canvas
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/**
 * Mini 3D globe lat/lng picker using BabylonJS (already in project).
 * - Shows day/night via a directional light matching the sun direction.
 * - Click to pick lat/lng (updates via onChange).
 * - Marker updates when lat/lng props change.
 */
@Composable
fun LatLngPicker3D(
    lat: Double?,
    lng: Double?,
    enabled: Boolean = true,
    onChange: (Double, Double) -> Unit,
) {
    // Unique canvas id for this instance
    val canvasId = remember { "latlng-picker-canvas-" + Random.nextInt(1_000_000) }

    // Local state for Babylon resources to ensure proper disposal
    var engine by remember { mutableStateOf<Engine?>(null) }
    var scene by remember { mutableStateOf<Scene?>(null) }
    var camera by remember { mutableStateOf<ArcRotateCamera?>(null) }
    var light by remember { mutableStateOf<DirectionalLight?>(null) }
    var earth by remember { mutableStateOf<Mesh?>(null) }
    var marker by remember { mutableStateOf<Mesh?>(null) }
    var markerPin by remember { mutableStateOf<Mesh?>(null) }

    // Render container and canvas
    Div(
        attrs = {
            // Minimal inline sizing per project patterns in editor forms
            style {
                property("width", "320px")
                property("height", "240px")
            }
        }
    ) {
        Canvas(
            attrs = {
                id(canvasId)
                style {
                    property("width", "100%")
                    property("height", "100%")
                    property("display", "block")
                    property("outline", "none")
                    property("background-color", "transparent")
                }
            }
        )
    }

    // Initialize Babylon when the canvas is in the DOM
    DisposableEffect(canvasId) {
        val canvas = document.getElementById(canvasId) as HTMLCanvasElement?
        if (canvas != null) {
            val eng = Engine(
                canvas = canvas,
                antialias = true,
                options = object : EngineOptions {
                    override var alpha: Boolean? = true
                }
            )
            engine = eng

            val scn = Scene(eng)
            scene = scn

            // Transparent background for the mini scene
            try {
                scn.clearColor = Color4(0f, 0f, 0f, 0f)
            } catch (_: Throwable) {}

            // Camera looking at origin; constrain for easy globe viewing
            val cam = ArcRotateCamera(
                name = "pickerCam",
                alpha = (-PI / 2).toFloat(),
                beta = (PI / 2.2).toFloat(),
                radius = 3.0f,
                target = Vector3(0f, 0f, 0f),
                scene = scn,
            )
            cam.attachControl()
            // Limit zoom
            cam.lowerRadiusLimit = 3f
            cam.upperRadiusLimit = 5f
            camera = cam

            val pipeline = DefaultRenderingPipeline(
                name = "pbrPipeline",
                hdr = true,
                scene = scn,
                cameras = arrayOf(camera)
            ).apply {
                bloomEnabled = true
                bloomThreshold = 0.0f
                bloomWeight = 0.25f
                bloomKernel = 32f
            }

            // If props provide an initial lat/lng, center the camera on that location
            run {
                val la = lat
                val lo = lng
                if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                    val latRad = (la * PI / 180.0)
                    val lonRad = (lo * PI / 180.0)
                    cam.alpha = lonRad.toFloat()
                    cam.beta = (PI / 2 - latRad).toFloat()
                }
            }

            // Directional light (sun) — yaw 180° relative to -sun so the lit hemisphere matches the texture orientation
            val sunDir = SunMath.computeSunDirectionUtc(kotlin.js.Date().getTime())
            val lightDir = Vector3((sunDir.first).toFloat(), (-sunDir.second).toFloat(), (sunDir.third).toFloat())
            val dirLight = DirectionalLight("pickerSun", lightDir, scn)
            dirLight.intensity = 12.0f
            light = dirLight

            // Earth sphere
            val earthMesh = MeshBuilder.CreateSphere(
                name = "earth",
                options = object : CreateSphereOptions {
                    override val diameter = 2.0f
                    override val segments = 32
                },
                scene = scn,
            )
            val mat = PBRMaterial("earthMat", scn)
            val earthTex = Texture(
                url = "raw/data/ui/earth.png",
                scene = scn,
                options = object : TextureOptions {
                    override val samplingMode = Texture.NEAREST_SAMPLINGMODE
                    override val invertY = false
                }
            ).apply {
                uScale = -1f
                uOffset = 0.5f
            }
            mat.albedoTexture = earthTex
            mat.metallic = 0f
            mat.roughness = 1f
            // Faint emission so night side is slightly visible
            mat.emissiveTexture = earthTex
            mat.emissiveColor = Color3(0.01f, 0.01f, 0.01f)
            earthMesh.material = mat
            earth = earthMesh

            // Marker sphere (small emissive look using diffuse color)
            val markerMesh = MeshBuilder.CreateSphere(
                name = "marker",
                options = object : CreateSphereOptions {
                    override val diameter = 0.06f
                    override val segments = 8
                },
                scene = scn,
            )
            val markerMat = PBRMaterial("markerMat", scn)
            markerMat.albedoColor = Color3(1f, 0.1f, 0.1f)
            markerMat.emissiveColor = Color3(1f, 0.1f, 0.1f)
            markerMesh.material = markerMat
            marker = markerMesh

            // Upsert or update the white pin tube that connects marker to surface (length 0.1)
            fun upsertMarkerPinForUnitVector(vx: Float, vy: Float, vz: Float) {
                val surface = Vector3(vx, vy, vz)
                val tip = Vector3(vx, vy, vz).apply { scaleInPlace(1.1f) }
                val existing = markerPin
                if (existing == null) {
                    val tube = MeshBuilder.CreateTube(
                        name = "markerPin",
                        options = object : CreateTubeOptions {
                            override val path = arrayOf(surface, tip)
                            override val radius = 0.005f
                            override val tessellation = 6
                            override val updatable = true
                        },
                        scene = scn,
                    )
                    val pinMat = PBRMaterial("markerPinMat", scn)
                    pinMat.albedoColor = Color3(.75f, .75f, .75f)
                    pinMat.emissiveColor = Color3(.25f, .25f, .25f)
                    tube.material = pinMat
                    markerPin = tube
                } else {
                    MeshBuilder.CreateTube(
                        name = "markerPin",
                        options = object : CreateTubeOptions {
                            override val path = arrayOf(surface, tip)
                            override val radius = 0.005f
                            override val tessellation = 6
                            override val instance = existing
                        },
                        scene = scn,
                    )
                }
            }

            // Initial marker position from props
            fun updateMarkerFromProps() {
                val la = lat
                val lo = lng
                if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                    // Map UI longitude directly (texture rotated 180° via uOffset)
                    val geoLon = lo
                    val v = SunMath.toUnitVectorFromLatLng(la, geoLon)
                    markerMesh.position = Vector3(v.first.toFloat(), v.second.toFloat(), v.third.toFloat())
                    // Place slightly above surface
                    markerMesh.position.scaleInPlace(1.1f)
                    upsertMarkerPinForUnitVector(v.first.toFloat(), v.second.toFloat(), v.third.toFloat())
                }
            }
            updateMarkerFromProps()

            // Drag detection variables
            var startX = 0.0
            var startY = 0.0
            var moved = false
            val thresholdPx = 5.0

            val onPointerDown: (Event) -> Unit = let@ { e ->
                if (!enabled) return@let
                val pe = e as? PointerEvent ?: return@let
                startX = pe.offsetX
                startY = pe.offsetY
                moved = false
            }

            val onPointerMove: (Event) -> Unit = let@ { e ->
                if (!enabled) return@let
                val pe = e as? PointerEvent ?: return@let
                val dx = pe.offsetX - startX
                val dy = pe.offsetY - startY
                if (!moved && (abs(dx) > thresholdPx || abs(dy) > thresholdPx)) moved = true
            }

            val onPointerUp: (Event) -> Unit = let@ { e ->
                if (!enabled) return@let
                val pe = e as? PointerEvent ?: return@let
                if (moved) {
                    // User dragged to rotate; do not pick
                    return@let
                }
                // Compute pick ray
                val ray = scn.createPickingRay(
                    pe.offsetX.toInt(),
                    pe.offsetY.toInt(),
                    Matrix.Identity(),
                    cam,
                )
                val hit = ray.intersectsMesh(earthMesh)
                val p = hit.pickedPoint
                if (p != null) {
                    // Convert to local unit sphere; earth is centered at origin with radius=1 (unit) scaled to diameter 2
                    val len = p.length()
                    if (len > 0f) {
                        val nx = (p.x / len).toDouble()
                        val ny = (p.y / len).toDouble()
                        val nz = (p.z / len).toDouble()
                        val (la0, lo0) = SunMath.toLatLngFromUnitVector(nx, ny, nz)
                        // Clamp to valid geographic ranges
                        val la = la0.coerceIn(-90.0, 90.0)
                        val lo = lo0.coerceIn(-180.0, 180.0)
                        onChange(la, lo)
                        // Update marker immediately (already in geometry space)
                        markerMesh.position = Vector3(nx.toFloat(), ny.toFloat(), nz.toFloat()).apply { scaleInPlace(1.1f) }
                        upsertMarkerPinForUnitVector(nx.toFloat(), ny.toFloat(), nz.toFloat())
                    }
                }
            }

            canvas.addEventListener("pointerdown", onPointerDown)
            canvas.addEventListener("pointermove", onPointerMove)
            canvas.addEventListener("pointerup", onPointerUp)

            // Resize handling
            val resizeHandler: (Event) -> Unit = {
                eng.resize()
            }
            window.addEventListener("resize", resizeHandler)

            // Render loop
            eng.runRenderLoop { scn.render() }

            // Cleanup
            onDispose {
                try { canvas.removeEventListener("pointerdown", onPointerDown) } catch (_: Throwable) {}
                try { canvas.removeEventListener("pointermove", onPointerMove) } catch (_: Throwable) {}
                try { canvas.removeEventListener("pointerup", onPointerUp) } catch (_: Throwable) {}
                try { window.removeEventListener("resize", resizeHandler) } catch (_: Throwable) {}
                try { eng.dispose() } catch (_: Throwable) {}
                engine = null
                scene = null
                camera = null
                light = null
                earth = null
                marker = null
                markerPin = null
            }
        } else {
            // No canvas found; nothing to dispose
            onDispose {}
        }
    }

    // Update sun light direction every 60s (and on mount)
    LaunchedEffect(Unit) {
        fun applySun() {
            val l = light ?: return
            val sun = SunMath.computeSunDirectionUtc(kotlin.js.Date().getTime())
            // Yaw 180° relative to -sun direction to align visibly with texture orientation
            l.direction = Vector3((-sun.first).toFloat(), (-sun.second).toFloat(), (-sun.third).toFloat())
        }
        applySun()
        while (true) {
            kotlinx.coroutines.delay(60_000)
            applySun()
        }
    }

    // Sync marker with lat/lng prop changes
    LaunchedEffect(lat, lng) {
        val m = marker ?: return@LaunchedEffect
        val scn = scene ?: return@LaunchedEffect
        val la = lat
        val lo = lng
        if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
            // Map UI longitude directly (texture rotated 180° via uOffset)
            val geoLon = lo
            val v = SunMath.toUnitVectorFromLatLng(la, geoLon)
            val vx = v.first.toFloat()
            val vy = v.second.toFloat()
            val vz = v.third.toFloat()
            m.position = Vector3(vx, vy, vz).apply { scaleInPlace(1.1f) }
            // Create or update the pin tube here as well
            val surface = Vector3(vx, vy, vz)
            val tip = Vector3(vx, vy, vz).apply { scaleInPlace(1.1f) }
            val existing = markerPin
            if (existing == null) {
                val tube = MeshBuilder.CreateTube(
                    name = "markerPin",
                    options = object : CreateTubeOptions {
                        override val path = arrayOf(surface, tip)
                        override val radius = 0.01f
                        override val tessellation = 8
                        override val updatable = true
                    },
                    scene = scn,
                )
                val pinMat = PBRMaterial("markerPinMat", scn)
                pinMat.albedoColor = Color3(.75f, .75f, .75f)
                pinMat.emissiveColor = Color3(.25f, .25f, .25f)
                tube.material = pinMat
                markerPin = tube
            } else {
                MeshBuilder.CreateTube(
                    name = "markerPin",
                    options = object : CreateTubeOptions {
                        override val path = arrayOf(surface, tip)
                        override val radius = 0.005f
                        override val tessellation = 6
                        override val instance = existing
                    },
                    scene = scn,
                )
            }
        }
    }
}
