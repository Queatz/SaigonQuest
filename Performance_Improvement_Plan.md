# Saigon.Quest Performance Improvement Plan

This document outlines identified performance bottlenecks in Saigon.Quest and proposes a prioritized plan for optimization.

## Identified Bottlenecks

### 1. CPU: Redundant Bounding Box Calculations (High Impact)
*   **Location**: `WorldObjectManager.update` and `SceneObjectManager.updateObjectFloatingUiPosition`.
*   **Issue**: Floating UI elements (typing indicators, status text) recalculate their Y-offset by iterating over all child meshes and computing world matrices every frame if *any* animation is playing in the entire scene.
*   **Effect**: Massive CPU spike in crowded areas with multiple animated characters.

### 2. GPU: Shadow Rendering Overhead (High Impact)
*   **Location**: `ShadowManager.kt`.
*   **Issue**: Shadow map resolution is very high (4096), and there is no distance culling. Every object added to the scene remains a shadow caster even if it is far away and barely visible.
*   **Effect**: High GPU usage and lower frame rates on mid-range hardware.

### 3. CPU/GC: Frequent Array Allocations in Update Loops (Medium Impact)
*   **Location**: `WorldObjectManager.update` and `SceneObjectManager.update`.
*   **Issue**: Repeatedly calling `getChildMeshes(false)` followed by `.filter { !DepthPicking.isStatusBillboardMesh(it) }` every frame for every object.
*   **Effect**: High garbage collection (GC) pressure and unnecessary CPU cycles spent on array traversal.

### 4. GPU: Complex Outline Shader (Medium Impact)
*   **Location**: `CameraPipelineManager.kt` (saigonOutlineV14PixelShader).
*   **Issue**: The shader performs 6 calls to a complex 3D noise function (`smoothNoise3D`) and 4 extra texture samples per pixel.
*   **Effect**: High fragment shader load, especially at high resolutions.

### 5. CPU: O(N) Proximity Checks (Medium Impact)
*   **Location**: `WorldObjectManager.maybeCheckProximity`.
*   **Issue**: Iterates through all backend nodes to check distance to the player every 500ms.
*   **Effect**: Performance degrades linearly as more objects are added to the world.

---

## Proposed Optimization Plan

| Priority | Task | Expected Boost | Category |
| :--- | :--- | :--- | :--- |
| 1 | **Floating UI Caching**: Cache bounding box offsets and only update if the specific object's animations change or it moves significantly. | **Very High** | CPU | ✓ |
| 2 | **Shadow Distance Culling**: Dynamically add/remove meshes from the shadow generator based on distance to camera. (Improved for big scenes) | **High** | GPU | ✓ |
| 3 | **Mesh Caching**: Cache the filtered list of meshes for each world object handle to avoid per-frame allocations. | **Medium** | CPU/GC | ✓ |
| 4 | **Shadow Map Downscaling**: Reduce default shadow map size to 2048 and further to 1024 in Low Graphics mode. | **Medium** | GPU | ✓ |
| 5 | **Noise Optimization**: Replace procedural `smoothNoise3D` in the outline shader with a pre-calculated noise texture (Blue Noise). | **Medium** | GPU |
| 6 | **Spatial Hashing**: Implement a simple grid-based lookup for proximity and interaction checks. | **Medium** | CPU | ✓ |

## Implementation Strategy

We will tackle these optimizations one by one, starting with the **Floating UI Caching** as it provides the most immediate relief for CPU-bound scenarios in crowded scenes.
