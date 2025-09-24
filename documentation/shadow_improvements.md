# Shadow Improvements

This document describes the changes made to improve shadow quality and ensure the player casts shadows in the Saigon.Quest project.

## Changes Made

### 1. Increased Shadow Resolution

The shadow map size in the `ShadowManager` class was increased from 2048 to 4096 to provide higher resolution shadows:

```kotlin
// Before
private val shadowMapSize = 2048

// After
private val shadowMapSize = 4096
```

This change doubles the resolution of the shadow map, resulting in sharper and more detailed shadows. Note that this may have a performance impact on lower-end devices.

### 2. Ensured Player Casts Shadows

Added code in the `Scene` component to register the player mesh as a shadow caster after it's created:

```kotlin
// Register player mesh as shadow caster
playerController.getPlayer()?.let { playerMesh ->
    worldLoader.getShadowManager()?.addShadowCaster(playerMesh)
    println("[DEBUG] Player mesh registered as shadow caster")
}
```

This ensures that the player character casts shadows just like other objects in the scene.

### 3. Fine-tuned Shadow Quality Parameters

Reduced the `normalBias` value in the `ShadowManager` from 0.01f to 0.005f for more precise shadows:

```kotlin
// Before
normalBias = 0.01f  // Reduced for more precise shadows

// After
normalBias = 0.005f  // Further reduced for even more precise shadows
```

A lower `normalBias` value results in shadows that appear closer to the objects casting them, reducing the "floating shadow" effect.

## Expected Results

These changes should result in:

1. Higher quality shadows with more detail and less pixelation
2. The player character casting shadows as it moves around the scene
3. More precise shadows that appear closer to the objects casting them

## Performance Considerations

The increased shadow map size (4096x4096) may impact performance on lower-end devices. If performance issues are observed, consider:

1. Adding a quality setting to adjust the shadow map size based on device capabilities
2. Implementing dynamic shadow quality based on scene complexity
3. Providing an option to disable shadows for very low-end devices

## Future Improvements

Potential future improvements to the shadow system could include:

1. Implementing contact-hardening shadows for more realistic results
2. Adding soft shadows with variable penumbra
3. Implementing shadow filtering techniques for smoother shadow edges
4. Adding shadow fade-out at distance to improve performance
