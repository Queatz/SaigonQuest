# CustomShaderNameResolve Implementation Fix

## Issue Description

The project was using an incorrect implementation of `customShaderNameResolve` in the OcclusionManager class. The implementation was returning a JavaScript object with a `processFinalCode` function, rather than setting this function on the `options` parameter as expected by BabylonJS.

## Root Cause Analysis

1. **Incorrect Function Signature**: The `customShaderNameResolve` function was defined with only one parameter (`shaderName`), but BabylonJS expects multiple parameters including `options`.

2. **Incorrect Return Value**: The function was returning a JavaScript object with a `processFinalCode` property, but BabylonJS expects the function to set `options.processFinalCode` and return the shader name.

3. **Missing Type Definitions**: The Kotlin wrapper for BabylonJS (`Bjs.kt`) had an incomplete type definition for the `customShaderNameResolve` function.

## Changes Made

### 1. Updated Type Definition in Bjs.kt

Updated the `customShaderNameResolve` function signature in `Bjs.kt` to include all the necessary parameters:

```kotlin
/**
 * Custom shader name resolver
 * 
 * @param shaderName The name of the shader
 * @param uniforms The list of uniforms
 * @param uniformBuffers The list of uniform buffers
 * @param samplers The list of samplers
 * @param defines The list of defines
 * @param attributes The list of attributes
 * @param options The options to customize the shader
 * @return The resolved shader name or options
 */
var customShaderNameResolve: ((
    shaderName: String,
    uniforms: dynamic,
    uniformBuffers: dynamic,
    samplers: dynamic,
    defines: dynamic,
    attributes: dynamic,
    options: dynamic
) -> dynamic)?
```

### 2. Updated Implementation in OcclusionManager.kt

Changed the implementation of `customShaderNameResolve` to set `options.processFinalCode` and return the shader name:

```kotlin
material.customShaderNameResolve = { shaderName, uniforms, uniformBuffers, samplers, defines, attributes, options ->
    // Set the processFinalCode function on the options object
    js("""
    options.processFinalCode = function(type, code) {
        if (type === "vertex") {
            console.log('Processing vertex shader code');
            return code + "\n#include<occlusionFaderVertexShader>";
        }
        else if (type === "fragment") {
            console.log('Processing fragment shader code');
            return code + "\n#include<occlusionFaderPixelShader>";
        }
        return code;
    }
    """)
    
    // Return the original shader name to use the default shader with our modifications
    shaderName
}
```

## Benefits of the Fix

1. **Correct BabylonJS Integration**: The implementation now follows the expected pattern for `customShaderNameResolve` in BabylonJS.

2. **Improved Debugging**: Added console.log statements to help with debugging shader processing.

3. **Better Type Safety**: The updated type definition in `Bjs.kt` provides better type safety and documentation.

4. **Preserved Functionality**: The occlusion fading effect still works as before, but with a more correct implementation.

## Technical Details

### How customShaderNameResolve Works in BabylonJS

In BabylonJS, the `customShaderNameResolve` function is called when a material needs to determine which shader to use. The function receives several parameters, including an `options` object that can be used to customize the shader processing.

By setting `options.processFinalCode` to a function, we can modify the shader code before it's compiled. This allows us to inject our custom shader code into the default shader, preserving all the default functionality while adding our custom behavior.

The function should return the name of the shader to use. By returning the original `shaderName`, we tell BabylonJS to use the default shader for the material, but with our modifications applied through `options.processFinalCode`.

### Shader Inclusion Mechanism

Our implementation uses the `#include` directive to include our custom shader code:

```glsl
return code + "\n#include<occlusionFaderVertexShader>";
```

This works because we've registered our shader code with `ShaderStore.IncludesShadersStore` earlier in the code:

```kotlin
ShaderStore.IncludesShadersStore["${customShaderName}VertexShader"] = vertexShaderCode
ShaderStore.IncludesShadersStore["${customShaderName}PixelShader"] = fragmentShaderCode
```

## Best Practices for BabylonJS Shader Customization

1. **Use the Correct Function Signature**: Always include all the parameters in the `customShaderNameResolve` function, even if you don't use all of them.

2. **Set options.processFinalCode**: Instead of returning an object with a `processFinalCode` property, set `options.processFinalCode` to a function.

3. **Return the Shader Name**: Return the original `shaderName` to use the default shader with your modifications, or return a custom name to use a completely different shader.

4. **Use Console Logging for Debugging**: Add console.log statements to help with debugging shader processing.

5. **Register Shader Code Properly**: Use `ShaderStore.IncludesShadersStore` for shader extensions and `ShaderStore.ShadersStore` for complete shaders.

## Conclusion

By updating the `customShaderNameResolve` implementation to follow the correct pattern expected by BabylonJS, we've fixed the issue with shader customization in the OcclusionManager class. The occlusion fading effect now works correctly, and the code is more maintainable and better documented.
