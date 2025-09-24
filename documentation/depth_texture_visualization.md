# Depth Texture Visualization

## Overview

This document describes the implementation of a depth texture visualization feature for debugging purposes in the Saigon.Quest project. The feature allows developers to toggle between normal rendering and depth texture visualization to help debug depth-related issues.

## Background

Depth textures are used in 3D rendering for various effects such as:
- Depth of field (DOF)
- Screen Space Ambient Occlusion (SSAO)
- Shadows
- Post-processing effects

When these effects aren't working correctly, it can be helpful to visualize the depth texture directly to identify issues with depth writing, material transparency, or other depth-related problems.

## Implementation

The depth texture visualization feature consists of several components:

### 1. CameraController Enhancements

The `CameraController` class has been enhanced with methods to toggle depth texture visualization:

```kotlin
// Flag to track depth texture visualization state
private var isDepthTextureVisualizationEnabled: Boolean = false

// Toggle depth texture visualization
fun toggleDepthTextureVisualization(): Boolean {
    if (isDepthTextureVisualizationEnabled) {
        disableDepthTextureVisualization()
    } else {
        enableDepthTextureVisualization()
    }
    return isDepthTextureVisualizationEnabled
}

// Enable depth texture visualization
private fun enableDepthTextureVisualization() {
    scene.enableDepthRenderer()
    isDepthTextureVisualizationEnabled = true
}

// Disable depth texture visualization
private fun disableDepthTextureVisualization() {
    scene.disableDepthRenderer()
    isDepthTextureVisualizationEnabled = false
}

// Check if depth texture visualization is enabled
fun isDepthTextureVisualizationEnabled(): Boolean {
    return isDepthTextureVisualizationEnabled
}
```

### 2. SceneManager Singleton

A `SceneManager` singleton was created to provide global access to the `CameraController`:

```kotlin
object SceneManager {
    private var cameraController: CameraController? = null
    
    fun setCameraController(controller: CameraController) {
        cameraController = controller
    }
    
    fun getCameraController(): CameraController? {
        return cameraController
    }
    
    fun clear() {
        cameraController = null
    }
}
```

### 3. UI Integration

A debug button was added to the WorldUI component to toggle depth texture visualization:

```kotlin
// Debug button for depth texture visualization
CircleButton(
    onClick = { 
        val cameraController = SceneManager.getCameraController()
        if (cameraController != null) {
            isDepthTextureEnabled = cameraController.toggleDepthTextureVisualization()
        }
    },
    icon = if (isDepthTextureEnabled) "👁️" else "👁️‍🗨️"
)
```

## How to Use

To use the depth texture visualization feature:

1. Run the application
2. Look for the eye icon (👁️‍🗨️) in the top-right corner of the screen
3. Click the icon to toggle depth texture visualization
4. The icon will change to 👁️ when depth texture visualization is enabled
5. Click again to return to normal rendering

## Interpreting the Depth Texture

When depth texture visualization is enabled, the scene will be rendered in grayscale, where:
- Darker areas are closer to the camera
- Lighter areas are farther from the camera
- Black areas may indicate objects that aren't writing to the depth buffer

This visualization can help identify issues such as:
- Objects not writing to the depth buffer (appear black)
- Incorrect depth values (unexpected brightness/darkness)
- Z-fighting (flickering between different depth values)

## Technical Details

The depth texture visualization uses BabylonJS's built-in depth renderer functionality:
- `scene.enableDepthRenderer()` - Enables depth texture visualization
- `scene.disableDepthRenderer()` - Disables depth texture visualization

The depth renderer replaces the normal scene rendering with a visualization of the depth texture, which is useful for debugging but should be disabled for normal use.

## Related Files

- `/src/commonMain/kotlin/main/components/camera/CameraController.kt` - Contains the depth texture visualization methods
- `/src/commonMain/kotlin/main/components/SceneManager.kt` - Provides global access to the CameraController
- `/src/commonMain/kotlin/main/components/Scene.kt` - Registers the CameraController with the SceneManager
- `/src/commonMain/kotlin/main/components/WorldUI.kt` - Contains the UI button for toggling depth texture visualization

## Date

2025-07-29
