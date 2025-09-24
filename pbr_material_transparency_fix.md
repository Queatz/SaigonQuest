# PBR Material Transparency Fix

This document explains the changes made to fix transparency issues with character sprites in the Saigon.Quest project.

## Issue Description

The issue was that alpha (transparency) from the texture was not working correctly with the StandardMaterial that was being used for character sprites. Despite previous attempts to configure transparency settings, transparent areas of the sprite were not rendering correctly.

## Solution

The solution was to switch from using StandardMaterial to PBRMaterial, which handles transparency differently and often more effectively in BabylonJS.

### Changes Made

1. **Added PBRMaterial Import**
   
   Added the following import to the top of WorldObject.kt:
   
   `import lib.PBRMaterial`

2. **Replaced StandardMaterial with PBRMaterial in Character.render()**
   
   Before:
   ```kotlin
   val material = StandardMaterial("characterMaterial_${id}", scene)
   // ...
   material.diffuseTexture = texture
   
   // Enable transparency for the material
   material.backFaceCulling = false
   material.useAlphaFromDiffuseTexture = true
   material.transparencyMode = Material.MATERIAL_ALPHATEST
   material.alpha = 1.0f // Full opacity where the texture has content
   ```
   
   After:
   ```kotlin
   val material = PBRMaterial("characterMaterial_${id}", scene)
   // ...
   material.albedoTexture = texture
   
   // Enable transparency for the material
   material.backFaceCulling = false
   material.transparencyMode = Material.MATERIAL_ALPHABLEND
   material.alpha = 1.0f // Full opacity where the texture has content
   ```

3. **Updated updateSpriteAnimation() to work with PBRMaterial**
   
   Before:
   ```kotlin
   val material = mesh.material as? StandardMaterial
   material?.diffuseTexture?.let { texture ->
       // ...
   }
   ```
   
   After:
   ```kotlin
   val material = mesh.material as? PBRMaterial
   material?.albedoTexture?.let { texture ->
       // ...
   }
   ```

## Why This Fixes the Issue

1. **PBRMaterial vs StandardMaterial**: PBRMaterial is the default material used in Physically Based Rendering in BabylonJS. It often handles transparency better than StandardMaterial, especially for complex cases.

2. **MATERIAL_ALPHABLEND vs MATERIAL_ALPHATEST**: 
   - MATERIAL_ALPHATEST (used before) is a simple transparency mode where pixels are either fully transparent or fully opaque based on a threshold.
   - MATERIAL_ALPHABLEND (used now) enables smooth alpha blending, which respects the full range of alpha values in the texture.

3. **Removal of useAlphaFromDiffuseTexture**: This property is specific to StandardMaterial and isn't needed with PBRMaterial, which automatically uses the alpha channel from the albedo texture when transparency is enabled.

4. **albedoTexture vs diffuseTexture**: In PBR terminology, the base color texture is called "albedo" rather than "diffuse". The albedoTexture in PBRMaterial serves the same purpose as diffuseTexture in StandardMaterial.

## Testing

The changes have been tested to ensure:
1. Character sprites are rendered with proper transparency
2. The transparent areas of the sprite are correctly transparent
3. The character animations and direction changes continue to work correctly

## Conclusion

Switching from StandardMaterial to PBRMaterial with the appropriate transparency settings has fixed the issue with alpha from the texture not working correctly. The character sprites now render with proper transparency, improving the visual quality of the game.
