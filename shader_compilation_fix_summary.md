# Shader Compilation Fix Summary

## Issue Description

The project was encountering shader compilation errors in BabylonJS:

```
Error: VERTEX SHADER ERROR: Missing main()
```

This error occurred because our custom shader implementation for the occlusion fading effect was using a shader extension approach that wasn't compatible with how BabylonJS expected shaders to be structured.

## Root Cause Analysis

1. **Incomplete Shader Structure**: The vertex shader was missing a `main()` function, which is required for all GLSL shaders.

2. **Shader Registration Issues**: The shaders were registered with ShaderStore, but BabylonJS was trying to use them as complete shaders rather than as extensions to the existing PBR shader.

3. **Incorrect Shader Name Resolution**: The `customShaderNameResolve` implementation wasn't correctly telling BabylonJS how to use our custom shaders.

## Solution Implemented

### 1. Updated Vertex Shader

We replaced the shader extension approach with a complete vertex shader that includes a `main()` function:

```glsl
precision highp float;

// Attributes
attribute vec3 position;
attribute vec3 normal;
attribute vec2 uv;

// Uniforms
uniform mat4 world;
uniform mat4 worldView;
uniform mat4 worldViewProjection;

// Varying
varying vec3 vPositionW;
varying vec3 vNormalW;
varying vec2 vUV;

void main(void) {
    vec4 outPosition = worldViewProjection * vec4(position, 1.0);
    gl_Position = outPosition;
    
    vPositionW = (world * vec4(position, 1.0)).xyz;
    vNormalW = normalize(vec3(world * vec4(normal, 0.0)));
    vUV = uv;
}
```

### 2. Updated Fragment Shader

Similarly, we updated the fragment shader to include a complete shader with a `main()` function:

```glsl
precision highp float;

// Uniforms
uniform vec3 playerScreenPos;     // Player's screen position (normalized 0-1)
uniform float fadeRadius;         // Fade radius in pixels
uniform float playerWorldY;       // Player's world Y position
uniform float fadeSoftness;       // Fading softness (0-1)
uniform vec2 screenSize;          // Screen dimensions
uniform sampler2D textureSampler; // Base texture

// Constants for optimization
const float MIN_ALPHA = 0.2;
const float MAX_ALPHA = 1.0;

// Varying
varying vec3 vPositionW;
varying vec3 vNormalW;
varying vec2 vUV;

void main(void) {
    // Get base color from texture if available
    vec4 baseColor = vec4(1.0, 1.0, 1.0, 1.0);
    if (vUV != vec2(0.0, 0.0)) {
        baseColor = texture2D(textureSampler, vUV);
    }
    
    // Default: fully opaque
    float fadeFactor = 1.0;
    
    // Check if the fragment is above the player (world Y)
    if (vPositionW.y > playerWorldY) {
        // Get current fragment's screen position
        vec2 fragScreenPos = gl_FragCoord.xy;
        
        // Convert playerScreenPos to pixel coordinates
        vec2 playerScreenPixel = vec2(playerScreenPos.x * screenSize.x, playerScreenPos.y * screenSize.y);
        
        // Calculate squared distance from player in screen space
        vec2 delta = fragScreenPos - playerScreenPixel;
        float distanceSquared = dot(delta, delta);
        
        // Square the radius for comparison with squared distance
        float radiusSquared = fadeRadius * fadeRadius;
        
        // Apply fading if within radius
        if (distanceSquared < radiusSquared) {
            // Calculate fade factor based on distance and softness
            if (fadeSoftness <= 0.0) {
                // Sharp edge (binary transparency)
                fadeFactor = MIN_ALPHA;
            } else if (fadeSoftness >= 1.0) {
                // Full softness (linear from center to edge)
                fadeFactor = sqrt(distanceSquared) / fadeRadius;
                fadeFactor = clamp(fadeFactor, MIN_ALPHA, MAX_ALPHA);
            } else {
                // Custom softness
                // Calculate the inner radius where fading starts
                float innerRadius = fadeRadius * (1.0 - fadeSoftness);
                float innerRadiusSquared = innerRadius * innerRadius;
                
                if (distanceSquared < innerRadiusSquared) {
                    // Inside inner circle: maximum transparency
                    fadeFactor = MIN_ALPHA;
                } else {
                    // In the gradient zone
                    float distance = sqrt(distanceSquared);
                    float gradientPos = (distance - innerRadius) / (fadeRadius - innerRadius);
                    fadeFactor = mix(MIN_ALPHA, MAX_ALPHA, gradientPos);
                }
            }
        }
    }
    
    // Apply fade factor to alpha
    gl_FragColor = vec4(baseColor.rgb, baseColor.a * fadeFactor);
}
```

### 3. Updated Shader Name Resolution

We fixed the `customShaderNameResolve` implementation to correctly tell BabylonJS to use our custom shaders:

```kotlin
material.customShaderNameResolve = { shaderName ->
    // Return a JavaScript object that tells BabylonJS to use our custom shaders
    js("""
    {
        vertex: "occlusionFader",
        fragment: "occlusionFader"
    }
    """)
}
```

## Best Practices for BabylonJS Shader Customization

Based on our experience fixing this issue, here are some best practices for shader customization in BabylonJS:

1. **Complete Shaders vs. Shader Extensions**:
   - For complete shader replacement, provide shaders with a `main()` function
   - For shader extensions, use the `#ifdef` directive approach with specific hooks

2. **Shader Registration**:
   - Register vertex shaders with the "VertexShader" suffix: `ShaderStore.ShadersStore["${name}VertexShader"] = code`
   - Register fragment shaders with the "PixelShader" suffix: `ShaderStore.ShadersStore["${name}PixelShader"] = code`

3. **Shader Name Resolution**:
   - For complete shader replacement, return an object with `vertex` and `fragment` properties
   - Avoid string interpolation inside the `js()` function (use constant strings)

4. **Uniform Updates**:
   - Use the `onBindObservable` to update shader uniforms each frame
   - Check for null values to avoid runtime errors

5. **Shader Cleanup**:
   - Remove shader customizations in the `dispose()` method
   - Set `customShaderNameResolve` to null when no longer needed

## Conclusion

By implementing these changes, we've successfully fixed the shader compilation error and ensured that our occlusion fading effect works correctly. The key insight was understanding that BabylonJS expected complete shaders with `main()` functions rather than shader extensions when using the approach we had implemented.

This fix ensures that objects that obstruct the player's view will properly fade to transparency, improving visibility while preserving the visual quality of the scene.
