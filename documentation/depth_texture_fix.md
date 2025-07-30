# Depth Texture Fix

## Issue
The depth texture was not working correctly in the 3D world, which was suspected to be related to material transparency settings. When materials are transparent or have alpha blending enabled, they might not write to the depth buffer by default, which can cause issues with depth textures.

## Solution
Modified the `applyMaterials` method in `WorldLoader.kt` to ensure all meshes loaded are not transparent and will render to the depth texture correctly. The following properties were set for all PBR materials:

1. `alpha = 1.0f` - Makes materials fully opaque
2. `disableDepthWrite = false` - Ensures depth writing is enabled
3. `transparencyMode = 0` - Sets the transparency mode to OPAQUE
4. `alphaMode = 0` - Sets the alpha mode to ALPHA_DISABLE

## Technical Details

### Material Transparency and Depth Textures
In 3D rendering, depth textures are used for various effects like shadows, depth of field, and screen space ambient occlusion. For these effects to work correctly, all objects in the scene need to write their depth information to the depth buffer.

When materials are transparent or have alpha blending enabled, they might not write to the depth buffer by default, which can cause issues with depth-dependent effects. By ensuring all materials are fully opaque and have depth writing enabled, we ensure that all objects contribute correctly to the depth texture.

### BabylonJS Material Properties
The following properties were used to control transparency and depth writing:

- `alpha`: Controls the overall transparency of the material (1.0 = fully opaque)
- `disableDepthWrite`: When set to false, ensures the material writes to the depth buffer
- `transparencyMode`: Determines how transparency is handled (0 = OPAQUE)
- `alphaMode`: Controls how alpha is processed (0 = ALPHA_DISABLE)

### Debug Logging
Added debug logging to verify the transparency settings for each material:

```kotlin
println("[DEBUG] Material settings for ${mesh.name}: alpha=${alpha}, disableDepthWrite=${disableDepthWrite}, transparencyMode=${transparencyMode}, alphaMode=${alphaMode}")
```

This logging helps verify that all materials have the correct settings for proper depth texture rendering.

## Related Files
- `/home/jacobferrero/IdeaProjects/Saigon.Quest/src/commonMain/kotlin/main/components/world/WorldLoader.kt`

## Date
2025-07-29
