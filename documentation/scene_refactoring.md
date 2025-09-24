# Scene Component Refactoring Documentation

## Overview

This document describes the refactoring of the `Scene.kt` file into a more modular, maintainable architecture. The original file was over 1000 lines long and contained multiple responsibilities, making it difficult to maintain and debug. The refactoring split the code into several controller classes, each with a single responsibility.

## Motivation

The refactoring was motivated by several issues:

1. **Code Size**: The original `Scene.kt` file was over 1000 lines long, making it difficult to navigate and understand.
2. **Mixed Responsibilities**: The file handled camera management, player movement, input handling, world loading, and object management all in one place.
3. **Camera Panning Issue**: When panning the camera (with Shift+mouse), the player was being moved along with the camera.
4. **Maintainability Concerns**: The monolithic structure made it difficult to modify or extend functionality without affecting other parts of the code.

## New Architecture

The refactored code follows a controller-based architecture, with each controller responsible for a specific aspect of the scene:

### Controller Classes

1. **CameraController** (`src/commonMain/kotlin/main/components/camera/CameraController.kt`)
   - Handles camera creation and configuration
   - Manages camera target (separate from player)
   - Updates depth of field focus
   - Provides camera movement and positioning

2. **InputHandler** (`src/commonMain/kotlin/main/components/input/InputHandler.kt`)
   - Processes keyboard input (WASD and arrow keys)
   - Handles pointer input (click-to-move)
   - Calculates movement direction based on input

3. **PlayerController** (`src/commonMain/kotlin/main/components/player/PlayerController.kt`)
   - Creates and initializes the player mesh
   - Handles player movement and positioning
   - Validates positions for movement
   - Manages click-to-move functionality

4. **WorldLoader** (`src/commonMain/kotlin/main/components/world/WorldLoader.kt`)
   - Loads world data from the API
   - Loads 3D models
   - Applies materials to world meshes

5. **ObjectManager** (`src/commonMain/kotlin/main/components/object/ObjectManager.kt`)
   - Creates and manages objects in the scene
   - Handles object positioning
   - Processes object interactions
   - Manages WebSocket communication for objects

### Main Scene Component

The main `SceneComponent` in `Scene.kt` now:
- Initializes all controllers
- Coordinates between controllers
- Manages the UI state (loading, error messages, etc.)
- Sets up the render loop
- Handles cleanup when the component is disposed

## Key Improvements

### 1. Fixed Camera Panning Issue

The camera panning issue was fixed by:
- Creating a separate camera target object (invisible sphere)
- Making the camera target follow the player when the player moves
- Updating the camera to target this object instead of the player directly
- Updating the depth of field focus based on the camera target

This decouples camera panning from player movement, allowing the camera to be panned independently without affecting the player's position.

### 2. Improved Code Organization

- **Reduced File Size**: The main `Scene.kt` file was reduced from 1057 lines to 414 lines.
- **Separation of Concerns**: Each controller handles a specific aspect of the scene.
- **Cleaner Dependencies**: Controllers have clear dependencies on each other.

### 3. Enhanced Maintainability

- **Easier Debugging**: Issues can be isolated to specific controllers.
- **Simpler Modifications**: Changes to one aspect (e.g., camera behavior) don't affect other aspects.
- **Better Testability**: Controllers can be tested in isolation.

### 4. Better Code Reuse

- **Reusable Components**: Controllers can be reused in other parts of the application.
- **Consistent Patterns**: Similar functionality is implemented consistently across controllers.

## Usage Example

The refactored `SceneComponent` initializes and uses the controllers as follows:

```kotlin
// Create controllers
val cameraController = CameraController(scene)
val playerController = PlayerController(scene)
val inputHandler = InputHandler(scene, playerController, cameraController)
val worldLoader = WorldLoader(scene)
val objectManager = ObjectManager(scene, cameraController)

// Load the world
worldLoader.loadWorld(url)
    .then { worldMeshes ->
        // Set world meshes in controllers
        playerController.setWorldMeshes(worldMeshes)
        objectManager.setWorldMeshes(worldMeshes)
        
        // Create the player
        playerController.createPlayer(startPosition)
        
        // Set up input handling
        inputHandler.setupKeyboardHandling()
        inputHandler.setupPointerHandling()
    }

// Set up the render loop
scene.registerBeforeRender {
    // Calculate movement direction from input
    val moveDirection = inputHandler.calculateMovementDirection()
    
    // Update player position
    val newPosition = playerController.updatePlayerPosition(moveDirection)
    
    // Update camera target if player moved
    if (newPosition != null) {
        cameraController.updateCameraTarget(newPosition)
    }
}
```

## Future Considerations

1. **Further Refactoring**: Consider extracting the UI components (loading screen, error messages) into separate components.
2. **Testing**: Add unit tests for each controller to ensure functionality is preserved during future changes.
3. **Performance Optimization**: Profile the application to identify and optimize performance bottlenecks.
4. **Feature Extensions**: The modular architecture makes it easier to add new features, such as:
   - More sophisticated camera controls
   - Advanced player movement (jumping, running, etc.)
   - Enhanced object interactions

## Conclusion

The refactoring of the `Scene.kt` file has significantly improved the codebase's maintainability, readability, and extensibility. The new architecture follows best practices for separation of concerns and provides a solid foundation for future development.

By addressing the camera panning issue and organizing the code into focused controllers, the application is now more robust and easier to work with. Future developers should find it much simpler to understand and modify the codebase.
