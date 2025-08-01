# Sprite Sheet Dimensions Fix

## Issue Description

The issue was in the Character class's `render` method where the sprite sheet dimensions were being set with hardcoded values:

```kotlin
// Set fixed dimensions for the sprite sheet (8 columns, 8 rows for directions)
spriteSheet.setImageDimensions(
    spriteSheet.cellWidthPx * spriteSheet.columns, 
    spriteSheet.cellHeightPx * 8 // 8 directions
)
```

The problem with this approach is that it assumes the sprite sheet always has exactly 8 rows (for 8 directions), which might not be accurate if the CharacterDirection enum is ever modified. This could lead to incorrect UV coordinate calculations and improper sprite rendering.

## Solution

The solution was to use the actual number of directions from the CharacterDirection enum instead of hardcoding the value:

```kotlin
// Set actual dimensions for the sprite sheet based on the number of directions
spriteSheet.setImageDimensions(
    spriteSheet.cellWidthPx * spriteSheet.columns,
    spriteSheet.cellHeightPx * CharacterDirection.entries.size // Use actual number of directions
)
```

## Why This Fixes the Issue

1. **Dynamic Calculation**: Instead of assuming a fixed number of rows, we now calculate it based on the actual number of directions in the CharacterDirection enum.

2. **Maintainability**: If the CharacterDirection enum is ever modified to add or remove directions, the code will automatically adjust without requiring changes to this part of the code.

3. **Accuracy**: The UV coordinate calculations in the SpriteSheet class depend on the correct image dimensions. By providing the actual dimensions, we ensure that the UV coordinates are calculated correctly, which leads to proper sprite rendering.

4. **Consistency**: This approach ensures that the sprite sheet dimensions are consistent with the actual number of directions used in the game, preventing potential visual glitches or rendering issues.

## Testing

The changes have been tested to ensure:

1. The sprite sheet dimensions are correctly set based on the actual number of directions
2. The character sprites are rendered correctly with proper UV coordinate mapping
3. The character animations and direction changes continue to work correctly

## Conclusion

This fix ensures that the sprite sheet dimensions are calculated dynamically based on the actual number of directions, rather than using a hardcoded value. This makes the code more robust, maintainable, and accurate.
