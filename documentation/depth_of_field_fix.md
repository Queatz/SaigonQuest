# Depth of Field Quality Fix

## Issue Description

The depth of field effect in the 3D scene was causing the entire scene to appear blurry, as if it was rendered at 1/4th the resolution. This created a poor visual experience for users.

## Root Cause

The issue was caused by two factors:

1. **Missing Depth Texture**: The depth of field effect was not using a depth texture, which is essential for high-quality depth calculations. Without a depth texture, the effect has to approximate depth values, resulting in a blurry appearance.

2. **High Blur Level**: The depth of field blur level was not explicitly set, which might have defaulted to a higher value, causing excessive blurring.

## Solution

The fix involved two key changes to the `CameraController` class:

1. **Enabling Depth Texture**:
   ```kotlin
   // Enable depth texture for better depth of field quality
   // This is equivalent to passing 'true' as the 5th argument to the constructor
   (pipeline.asDynamic()).useDepthTexture = true
   ```
   
   This enables the depth texture for the DefaultRenderingPipeline, which allows for more accurate depth calculations in the depth of field effect. Since the Kotlin interface doesn't directly support the 5th constructor parameter, we used JavaScript interop with `asDynamic()` to set the property directly.

2. **Setting Blur Level to Low**:
   ```kotlin
   depthOfFieldBlurLevel = DepthOfFieldEffectBlurLevel.Low // Set blur level to Low for better quality
   ```
   
   This explicitly sets the blur level to Low, which reduces the amount of blurring applied to out-of-focus areas, resulting in a sharper image.

## Technical Explanation

### Depth Texture

In BabylonJS, the depth texture is a special texture that stores the distance from the camera to each pixel in the scene. This information is crucial for effects like depth of field, which need to know how far objects are from the camera to determine how much blur to apply.

Without a depth texture, the depth of field effect has to approximate depth values based on the scene's geometry, which can result in inaccurate blurring and a generally lower-quality effect.

### Blur Levels

BabylonJS offers different blur levels for the depth of field effect:
- **Low**: Performs fewer sampling passes, resulting in a sharper image with less computational cost
- **Medium**: Balances quality and performance
- **High**: Performs more sampling passes, creating a smoother but more resource-intensive blur

By setting the blur level to Low, we reduce the amount of blurring applied to out-of-focus areas, which helps maintain the scene's visual clarity while still providing the depth of field effect.

## Benefits

These changes significantly improve the visual quality of the depth of field effect:

1. **Higher Resolution Appearance**: The scene no longer appears to be rendered at a lower resolution
2. **Better Depth Perception**: More accurate depth calculations enhance the 3D effect
3. **Improved Visual Clarity**: Reduced blurring maintains the sharpness of in-focus areas
4. **Maintained Performance**: Using the Low blur level ensures the effect doesn't impact performance significantly

## Future Considerations

1. **Depth Texture Resolution**: If needed, the resolution of the depth texture can be adjusted for even better quality
2. **Custom Blur Kernel**: For more advanced effects, a custom blur kernel could be implemented
3. **Performance Monitoring**: Keep an eye on performance, especially on lower-end devices, as depth textures can be resource-intensive

## References

- [BabylonJS DefaultRenderingPipeline Documentation](https://doc.babylonjs.com/typedoc/classes/babylon.defaultrenderingpipeline)
- [BabylonJS Depth of Field Effect Documentation](https://doc.babylonjs.com/features/featuresDeepDive/postProcesses/depthOfFieldEffect)
