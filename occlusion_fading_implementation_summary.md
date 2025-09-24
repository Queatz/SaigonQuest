# PBRMaterial with Custom Shader for Occlusion Fading - Implementation Summary

## Overview

This document summarizes the implementation of occlusion fading using PBRMaterial in the Saigon.Quest project. The implementation allows objects that obstruct the player's view to become transparent, improving visibility while preserving PBR lighting and material properties.

## Implementation Approach

### Previous Implementation

The previous implementation used a `ShaderMaterial` approach, which had several limitations:

1. It completely replaced the original material with a custom shader material
2. This lost all PBR lighting and material properties from the original material
3. It encountered compatibility issues with method names (`setFragment`/`setFragmentShader`)

### Initial Implementation

The initial implementation used a simpler approach:

1. **PBRMaterial Preservation**: Instead of replacing the material, we kept the original PBRMaterial
2. **Dynamic Alpha Adjustment**: We dynamically adjusted the alpha property of the material based on:
   - Whether the object is above the player in world space
   - The screen-space distance between the object and player
   - The configured fade softness parameter
3. **Original Properties Tracking**: We stored and restored original alpha values to ensure proper cleanup

This approach preserved all PBR lighting and material properties while providing the occlusion fading effect, but it operated on a per-mesh basis rather than per-fragment.

### Current Implementation

The current implementation uses a more advanced shader-based approach:

1. **Custom Shader Extension**: We extend PBRMaterial with custom shader code using:
   - EffectStore to register custom vertex and fragment shaders
   - customShaderNameResolve to tell PBRMaterial to use our custom shaders
   - onBindObservable to set shader uniforms each frame
   
2. **Per-Fragment Fading**: The fading is calculated for each fragment (pixel) in the fragment shader:
   - Each fragment's screen position is compared to the player's screen position
   - The distance calculation is performed on the GPU for better performance
   - The alpha value is adjusted per-fragment for more precise fading
   
3. **Optimized Calculations**: The shader includes several optimizations:
   - Early exit conditions to skip unnecessary calculations
   - Squared distance calculations to avoid expensive square root operations
   - Efficient gradient calculations for smooth fading

This approach provides better visual quality with per-fragment precision while maintaining all PBR lighting and material properties.

## Key Changes

### 1. Updated Bjs.kt

Added necessary interfaces and methods to the Kotlin wrapper for BabylonJS:

- Added `Effect` class with methods for setting shader uniforms
- Added `EffectStore` object for managing shader code
- Added properties and methods to `PBRMaterial` class for shader customization:
  - `onBindObservable` for setting shader uniforms
  - `getEffect()` for accessing the current shader
  - `customShaderNameResolve` for custom shader selection

### 2. Implemented Custom Shaders

Created and registered custom shader code:

- Vertex shader with hooks for BabylonJS shader customization
- Fragment shader with:
  - Custom uniform declarations
  - Per-fragment screen position calculation
  - Distance calculation from player in screen space
  - Fading logic based on distance and softness
  - Alpha modification

### 3. Refactored OcclusionManager.kt

Completely refactored the OcclusionManager class:

- Added custom shader registration in init block
- Modified registerMesh to configure PBRMaterial to use custom shaders
- Set up onBindObservable to update shader uniforms each frame
- Simplified update method (now a no-op for API compatibility)
- Enhanced dispose method to clean up shader resources

### 4. Optimized Shader Calculations

Implemented several optimizations in the fragment shader:

- Added early exit conditions to skip unnecessary calculations
- Used squared distances to avoid expensive square root operations
- Added constants for minimum and maximum alpha values
- Improved code structure with better organization

### 5. Maintained Compatibility with WorldLoader.kt

Ensured the new implementation works with the existing code:

- OcclusionManager is created with the same constructor signature
- Meshes are registered with the same registerMesh method
- The update method has the same signature (though implementation is different)
- The dispose method has the same signature and behavior

## Benefits of the New Implementation

1. **Per-Fragment Precision**: Fading is calculated for each fragment (pixel) rather than per-mesh, resulting in more precise and visually appealing fading effects
2. **Preserved PBR Lighting**: All PBR lighting and material properties are preserved
3. **GPU-Accelerated Calculations**: Distance calculations and fading logic are performed on the GPU, improving performance
4. **Better Visual Quality**: The per-fragment approach creates smoother transitions and more accurate occlusion effects
5. **Optimized Performance**: Early exit conditions and efficient distance calculations reduce computational overhead
6. **Better Compatibility**: The implementation is fully compatible with BabylonJS 8.18.0
7. **Proper Cleanup**: All resources are properly cleaned up on disposal
8. **Configurable Fading**: The fading softness parameter provides the same level of customization

## Technical Details

### Shader-Based Fading Algorithm

The fading algorithm is implemented in the fragment shader and works as follows:

1. **Early Exit Check**: First, check if the fragment is above the player in world space. If not, skip all calculations.

2. **Screen-Space Position Calculation**:
   - Get the current fragment's screen position using `gl_FragCoord.xy`
   - Convert the player's screen position from normalized [0,1] coordinates to pixel coordinates

3. **Optimized Distance Calculation**:
   - Calculate the squared distance between fragment and player in screen space
   - Use `dot(delta, delta)` for efficient squared distance calculation
   - Compare with squared radius to avoid unnecessary square root operations

4. **Early Exit for Fragments Outside Fade Radius**:
   - If the squared distance is greater than the squared radius, skip fading calculations

5. **Fade Factor Calculation** based on distance and softness:
   - For sharp edge (softness = 0): Binary transparency (either minimum alpha or fully opaque)
   - For full softness (softness = 1): Linear gradient from center to edge
   - For custom softness: Two-zone gradient with inner circle of maximum transparency

6. **Alpha Modification**:
   - Multiply the fragment's alpha value by the calculated fade factor

### Shader Extension Mechanism

The implementation uses BabylonJS's shader extension mechanism:

1. **Shader Registration**:
   - Custom shader code is registered with EffectStore
   - Vertex shader uses BabylonJS's shader inclusion system with `#ifdef` blocks
   - Fragment shader defines custom uniforms and implements fading logic

2. **Material Configuration**:
   - Each PBRMaterial is configured to use the custom shader via customShaderNameResolve
   - Shader uniforms are updated each frame via onBindObservable

3. **Uniform Updates**:
   - Player's screen position
   - Player's world Y position
   - Fade radius
   - Fade softness
   - Screen dimensions

### Performance Optimizations

The implementation includes several performance optimizations:

1. **GPU-Based Calculations**: All distance and fading calculations are performed on the GPU
2. **Early Exit Conditions**: Skip calculations for fragments that don't need fading
3. **Squared Distance Calculations**: Avoid expensive square root operations where possible
4. **Efficient Gradient Calculations**: Optimize the transition zone calculations
5. **Constant Values**: Use shader constants for minimum and maximum alpha values

## Future Improvements

While the current implementation provides significant improvements over the previous approach, there are still potential areas for enhancement:

1. **Depth-Based Fading**: Instead of using screen-space distance, depth information could be used for more accurate occlusion detection
2. **Adaptive Fade Radius**: The fade radius could adapt based on the scene complexity or camera distance
3. **Custom Fade Shapes**: Support for non-circular fade regions (e.g., elliptical or rectangular)
4. **Material-Specific Fading**: Different fading parameters for different material types
5. **Temporal Smoothing**: Add smooth transitions when objects enter or exit the fade region

## Conclusion

The new shader-based implementation of occlusion fading provides significant improvements in both visual quality and performance:

1. **Visual Quality**: By performing fading calculations per-fragment rather than per-mesh, the effect is more precise and visually appealing, especially for large or complex objects.

2. **Performance**: Moving calculations to the GPU and implementing various optimizations (early exits, squared distances, etc.) results in better performance, particularly for scenes with many objects.

3. **Flexibility**: The implementation maintains the same level of configurability while providing a more robust foundation for future enhancements.

4. **Compatibility**: The approach is fully compatible with BabylonJS 8.18.0 and preserves all PBR lighting and material properties.

This implementation successfully addresses the requirements for per-fragment fading using EffectStore and Effects, as well as optimizing the screen-space distance calculations for better performance. The result is a high-quality occlusion fading effect that enhances the player's experience by providing clear visibility without sacrificing visual fidelity.
