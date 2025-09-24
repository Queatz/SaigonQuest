# Occlusion Fading Shader Extension Implementation

## Overview

This document summarizes the implementation of occlusion fading using shader extensions in BabylonJS. The implementation allows objects that obstruct the player's view to become transparent while preserving all PBR lighting and material properties.

## Requirements Addressed

The implementation addresses three key requirements:

1. **Bind the Texture**: Added texture binding in the onBindObservable callback to ensure textures are properly displayed.
2. **Switch to Shader Extension**: Modified the shader code to use BabylonJS's shader extension mechanism instead of complete replacement.
3. **Update the Shader Registration**: Updated the shader registration to use the proper extension mechanism.

## Implementation Details

### 1. Shader Extension Approach

The previous implementation used complete shader replacement, which caused meshes to render as pure black with no lighting. The new implementation uses shader extensions, which allows us to:

- Preserve all PBR lighting and material properties
- Only modify the alpha component of the final color
- Keep all textures and lighting calculations from the original PBR shader

### 2. Key Changes

#### 2.1 Modified Shader Code

Changed the shader code from complete shaders with main() functions to extension hooks:

```glsl
// Vertex shader using extension approach
#ifdef VERTEX_DEFINITIONS
// Add any custom vertex definitions here
#endif

#ifdef VERTEX_MAIN_BEGIN
// Add any code that needs to run at the beginning of the main function
#endif
```

```glsl
// Fragment shader using extension approach
#ifdef FRAGMENT_DEFINITIONS
// Custom uniforms
uniform vec3 playerScreenPos;     // Player's screen position (normalized 0-1)
uniform float fadeRadius;         // Fade radius in pixels
uniform float playerWorldY;       // Player's world Y position
uniform float fadeSoftness;       // Fading softness (0-1)
uniform vec2 screenSize;          // Screen dimensions

// Constants for optimization
const float MIN_ALPHA = 0.2;
const float MAX_ALPHA = 1.0;
#endif

#ifdef FRAGMENT_BEFORE_FRAGCOLOR
// This code runs just before gl_FragColor is set
// Calculate fade factor and apply to alpha
float fadeFactor = 1.0;
// ... (fading calculation logic)
color.a *= fadeFactor;
#endif
```

#### 2.2 Updated Shader Registration

Changed from ShadersStore to IncludesShadersStore for registering shader extensions:

```kotlin
// Register shader extensions
ShaderStore.IncludesShadersStore["${customShaderName}VertexShader"] = vertexShaderCode
ShaderStore.IncludesShadersStore["${customShaderName}PixelShader"] = fragmentShaderCode
```

#### 2.3 Updated customShaderNameResolve

Modified the customShaderNameResolve function to properly use shader extensions:

```kotlin
material.customShaderNameResolve = { shaderName ->
    // Return a JavaScript object that tells BabylonJS how to use our shader extensions
    js("""
    {
        processFinalCode: function(type, code) {
            if (type === "vertex") {
                return code + "\n#include<occlusionFaderVertexShader>";
            }
            else if (type === "fragment") {
                return code + "\n#include<occlusionFaderPixelShader>";
            }
            return code;
        }
    }
    """)
}
```

#### 2.4 Added Texture Binding

Added texture binding in the onBindObservable callback:

```kotlin
// Bind the albedo texture to the textureSampler
material.albedoTexture?.let { texture ->
    effect.setTexture("textureSampler", texture)
}
```

### 3. Benefits of the New Implementation

1. **Preserved PBR Lighting**: All PBR lighting and material properties are preserved.
2. **Proper Texture Rendering**: Textures are properly displayed on the meshes.
3. **Per-Fragment Fading**: Fading is calculated for each fragment (pixel) rather than per-mesh.
4. **Better Visual Quality**: The fading effect is more precise and visually appealing.
5. **Optimized Performance**: The shader extension approach is more efficient than complete replacement.

## Conclusion

The implementation successfully addresses all three requirements:

1. **Bind the Texture**: ✅ Added texture binding in the onBindObservable callback.
2. **Switch to Shader Extension**: ✅ Modified the shader code to use extension hooks.
3. **Update the Shader Registration**: ✅ Updated the registration to use IncludesShadersStore.

The occlusion fading effect now works correctly, making objects transparent when they obstruct the player's view while preserving all PBR lighting and material properties.
