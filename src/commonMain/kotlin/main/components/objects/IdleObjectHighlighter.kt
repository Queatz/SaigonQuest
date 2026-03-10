package main.components.objects

import kotlinx.coroutines.*

/**
 * Automatically cycles through objects in the scene and highlights them
 * when the user is idle.
 */
class IdleObjectHighlighter(
    private val objectManager: SceneObjectManager,
    private val coroutineScope: CoroutineScope,
) {
    private var job: Job? = null

    /**
     * Starts the idle highlighting cycle.
     */
    fun start() {
        if (job != null) return

        job = coroutineScope.launch {
            while (isActive) {
                // Get fresh list of objects each cycle to account for added/removed objects
                val objectIds = objectManager.getObjectNodes().keys.toList().filter {
                    !objectManager.isInventoryObject(it)
                }
                if (objectIds.isEmpty()) {
                    delay(5000)
                    continue
                }

                for (objectId in objectIds) {
                    if (!isActive) break

                    // 1. Object fake-hovered for 5 seconds
                    console.log("[IdleHighlight] Hovering object: $objectId")
                    objectManager.highlightObject(objectId)
                    delay(5000)

                    if (!isActive) break

                    // 2. Object hover removed for 10 seconds
                    console.log("[IdleHighlight] Removing hover from object: $objectId")
                    objectManager.unhighlightObject()
                    delay(10000)
                }
            }
        }
    }

    /**
     * Stops the idle highlighting cycle and removes any current highlight.
     */
    fun stop() {
        job?.cancel()
        job = null
        objectManager.unhighlightObject()
    }

    /**
     * Returns true if the idle highlighting cycle is currently active.
     */
    fun isActive(): Boolean = job != null
}
