package main.components.objects

import lib.Animation
import lib.BounceEase
import lib.CubicEase
import lib.EasingFunction
import lib.Scene
import lib.Vector3
import main.components.world.WorldObjectHandle
import kotlin.math.abs

/**
 * Handles animations for object status text billboards.
 */
object ObjectStatusAnimator {
    /**
     * Animates the status text for an object (slide up + zoom in + bounce)
     *
     * @param objectId The ID of the object
     * @param handle The handle to the status text billboard
     * @param show Whether to show or hide the status text
     * @param targetY The target local Y position for the status text
     * @param scene The BabylonJS scene
     */
    fun animateStatusText(
        objectId: String,
        handle: WorldObjectHandle,
        show: Boolean,
        targetY: Float,
        scene: Scene
    ) {
        val node = handle.node
        
        if (show) {
            // Only start show animation if not already fully visible at correct position
            val isVisible = handle.isVisible
            val isScaled = node.scaling.x > 0.99f
            val isAtCorrectY = abs(node.position.y - targetY) < 0.01f
            
            if (isVisible && isScaled && isAtCorrectY) return
            
            // Stop any existing hide/show animation
            scene.stopAnimation(node, "statusScale")
            scene.stopAnimation(node, "statusPosition")
            
            handle.isVisible = true
            
            // If it was fully hidden, start from below and zero scale
            if (!isVisible || node.scaling.x < 0.01f) {
                node.scaling = Vector3(0f, 0f, 0f)
                node.position = Vector3(0f, targetY - 0.2f, 0f)
            }
            
            val bounce = BounceEase(2, 3f)
            bounce.setEasingMode(EasingFunction.EASINGMODE_EASEOUT)
            
            Animation.CreateAndStartAnimation(
                name = "statusScale",
                target = node,
                targetProperty = "scaling",
                framePerSecond = 60,
                totalFrame = 10,
                from = node.scaling,
                to = Vector3(1f, 1f, 1f),
                loopMode = Animation.ANIMATIONLOOPMODE_CONSTANT,
                easingFunction = bounce,
            )
            
            Animation.CreateAndStartAnimation(
                name = "statusPosition",
                target = node,
                targetProperty = "position",
                framePerSecond = 60,
                totalFrame = 10,
                from = node.position,
                to = Vector3(0f, targetY, 0f),
                loopMode = Animation.ANIMATIONLOOPMODE_CONSTANT,
                easingFunction = bounce,
            )
        } else {
            // Only start hide animation if visible
            if (!handle.isVisible) return
            
            // Stop any existing show/hide animation
            scene.stopAnimation(node, "statusScale")
            scene.stopAnimation(node, "statusPosition")
            
            val ease = CubicEase()
            ease.setEasingMode(EasingFunction.EASINGMODE_EASEIN)
            
            Animation.CreateAndStartAnimation(
                name = "statusScale",
                target = node,
                targetProperty = "scaling",
                framePerSecond = 60,
                totalFrame = 7,
                from = node.scaling,
                to = Vector3(0f, 0f, 0f),
                loopMode = Animation.ANIMATIONLOOPMODE_CONSTANT,
                easingFunction = ease,
            )
            
            Animation.CreateAndStartAnimation(
                name = "statusPosition",
                target = node,
                targetProperty = "position",
                framePerSecond = 60,
                totalFrame = 7,
                from = node.position,
                to = Vector3(0f, targetY - 0.2f, 0f),
                loopMode = Animation.ANIMATIONLOOPMODE_CONSTANT,
                easingFunction = ease,
                onAnimationEnd = {
                    handle.isVisible = false
                }
            )
        }
    }
}
