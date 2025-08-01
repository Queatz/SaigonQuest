# Character WorldObject Fixes

This document explains the changes made to fix issues with the Character WorldObject in the Saigon.Quest project.

## Issues Fixed

1. **Incorrect Aspect Ratio**: The plane mesh for character sprites needed to be in the exact 32w x 48h aspect ratio.
2. **Transparency Rendering**: Character sprites needed proper transparency rendering.

## Changes Made

### 1. Fixed Aspect Ratio

**Location**: `WorldObject.kt` in the `Character.render()` method

**Before**:
```kotlin
// Create a plane with the correct aspect ratio
val aspectRatio = spriteSheet.cellHeightPx.toFloat() / spriteSheet.cellWidthPx.toFloat()
val mesh = MeshBuilder.CreatePlane(
    "character_${id}",
    object : CreatePlaneOptions {
        override val width = 1f
        override val height = aspectRatio
    },
    scene
)
```

**After**:
```kotlin
// Create a plane with the exact 32w x 48h aspect ratio (48/32 = 1.5)
val mesh = MeshBuilder.CreatePlane(
    "character_${id}",
    object : CreatePlaneOptions {
        override val width = 1f
        override val height = 1.5f  // Fixed 48/32 aspect ratio
    },
    scene
)
```

**Explanation**:
- Instead of calculating the aspect ratio based on the sprite sheet cell dimensions, we now use a fixed value of 1.5 (which is 48/32)
- This ensures the plane mesh is always in the exact 32w x 48h aspect ratio as required
- The fixed value is more reliable and prevents any potential issues if the sprite sheet dimensions change

### 2. Enhanced Transparency Settings

**Location**: `WorldObject.kt` in the `Character.render()` method

**Before**:
```kotlin
// Enable transparency for the material
material.backFaceCulling = false
material.useAlphaFromDiffuseTexture = true
```

**After**:
```kotlin
// Enable transparency for the material
material.backFaceCulling = false
material.useAlphaFromDiffuseTexture = true
material.transparencyMode = 2 // ALPHA_BLEND mode for proper transparency
material.alpha = 1.0f // Full opacity where the texture has content
```

**Explanation**:
- Added `material.transparencyMode = 2` to set the transparency mode to ALPHA_BLEND
- Added `material.alpha = 1.0f` to ensure full opacity where the texture has content
- These settings, combined with the existing ones, ensure proper transparency rendering
- The ALPHA_BLEND mode (value 2) is the standard mode for rendering transparent textures in BabylonJS
- Setting alpha to 1.0 ensures that the non-transparent parts of the texture are fully opaque

## Testing

The changes have been tested to ensure:
1. The character sprite is displayed with the correct 32w x 48h aspect ratio
2. Transparent areas of the sprite are properly rendered as transparent
3. The character animations and direction changes continue to work correctly

## Conclusion

These changes ensure that character sprites in the Saigon.Quest project are rendered with the correct aspect ratio and proper transparency, improving the visual quality and accuracy of the game.
