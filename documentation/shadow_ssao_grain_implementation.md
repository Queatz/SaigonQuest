# Shadow, SSAO, and Grain Effect Implementation

This document describes the implementation of shadows, Screen Space Ambient Occlusion (SSAO), and grain effects in the Saigon.Quest project.

## Shadow Implementation

Shadows are implemented using BabylonJS's CascadedShadowGenerator, which provides high-quality shadows with good performance for small scenes with many moving objects.

### Key Components

1. **ShadowManager** (`main/components/shadow/ShadowManager.kt`)
   - Manages shadows globally for the entire scene
   - Uses CascadedShadowGenerator for better quality shadows
   - Configures optimal shadow settings for performance and quality
   - Provides methods to add/remove shadow casters dynamically
   - Handles shadow bias and filtering to reduce artifacts

2. **Light Configuration**
   - Position: (10f, 20f, 10f) - Positioned closer to the scene for more visible shadows
   - Direction: (-0.2f, -1f, -0.2f) - Slight angle for better shadow projection
   - Intensity: 1.5f - Increased for more pronounced shadows
   - Shadow Range: minZ=0.1f, maxZ=100f - Matches camera range

3. **Shadow Generator Settings**
   - Bias: 0.0005f - Reduced for more precise shadows
   - Normal Bias: 0.01f - Reduced for more precise shadows
   - Lambda: 0.7f - Higher value concentrates resolution closer to the camera
   - Transparent Shadows: Enabled
   - Stabilize Cascades: Enabled

### Shadow Configuration Process

1. The WorldLoader creates a directional light
2. The ShadowManager configures the light for optimal shadow casting
3. The ShadowManager creates and configures the CascadedShadowGenerator
4. The WorldLoader applies materials to meshes and configures them as shadow casters and receivers
5. All objects from .glb files are configured as both shadow casters and receivers

## SSAO Implementation

Screen Space Ambient Occlusion (SSAO) is implemented using BabylonJS's SSAO2RenderingPipeline, which provides subtle ambient occlusion effects to enhance scene depth and realism.

### Key Settings

- **Radius**: 0.3f - Small radius for subtle effect
- **Total Strength**: 0.4f - Subtle strength
- **Samples**: 16 - Moderate sample count for quality/performance balance
- **Max Z**: Matches camera maxZ (100f)

## Grain Effect Implementation

A subtle grain effect is implemented using the sharpen feature of BabylonJS's DefaultRenderingPipeline.

### Key Settings

- **Edge Amount**: 0.15f - Subtle edge enhancement
- **Color Amount**: 0.5f - Moderate color enhancement

## Troubleshooting

If shadows are not visible, check the following:

1. **Light Position and Direction**
   - Ensure the light is positioned correctly relative to the scene
   - Verify the light direction is pointing towards the scene

2. **Shadow Generator Settings**
   - Check bias and normalBias values
   - Verify lambda value is appropriate for the scene scale

3. **Mesh Configuration**
   - Ensure meshes are properly configured as shadow casters and receivers
   - Check for any filtering that might exclude meshes from shadow casting/receiving

4. **Debug Logging**
   - Review debug logs for any warnings or errors
   - Check mesh counts for shadow casters and receivers

## Performance Considerations

- CascadedShadowGenerator provides better quality shadows but may impact performance on lower-end devices
- SSAO is a relatively expensive effect and may impact performance on lower-end devices
- The grain effect has minimal performance impact

## Future Improvements

- Add quality settings to adjust shadow, SSAO, and grain effect parameters based on device performance
- Implement dynamic shadow quality based on scene complexity
- Add options to enable/disable effects for performance optimization
