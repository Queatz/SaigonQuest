package main.components.objects

import lib.Color3
import lib.Node
import lib.Scene
import lib.Vector3
import main.components.SceneManager
import main.components.world.WorldObjectHandle
import models.SavedPosition

class SavedPositionManager(private val scene: Scene) {
    private val spheres = mutableMapOf<String, Node>()
    private val labels = mutableMapOf<String, WorldObjectHandle>()
    private var isVisible = false

    fun setSavedPositions(positions: List<SavedPosition>) {
        // Clear existing spheres and labels
        spheres.values.forEach { it.dispose() }
        spheres.clear()

        val worldObjectManager = SceneManager.getObjectManager()?.worldObjectManager
        labels.values.forEach { it.remove() }
        labels.clear()

        val objectManager = SceneManager.getObjectManager() ?: return

        positions.forEach { pos ->
            val position = Vector3(pos.position.x, pos.position.y, pos.position.z)
            val sphere = objectManager.createFallbackSphere(
                name = "saved_pos_${pos.key}",
                position = position,
                rotation = Vector3.Zero(),
                scale = Vector3(1f, 1f, 1f),
                color = Color3(0f, 1f, 1f), // Cyan
                emissiveColor = Color3(0f, 1f, 1f),
                diameter = 0.3f, // Small sphere
                isPickable = false
            )
            sphere.setEnabled(isVisible)
            spheres[pos.key] = sphere

            // Create label
            val labelHandle = worldObjectManager?.createTextBillboard(
                text = pos.name,
                position = position.add(Vector3(0f, 0.4f, 0f)), // Slightly above the sphere
                fontSize = 20,
                textColor = "cyan",
                backgroundColor = "rgba(0, 0, 0, 0.5)",
            )
            if (labelHandle != null) {
                labelHandle.node.setEnabled(isVisible)
                labels[pos.key] = labelHandle
            }
        }
    }

    fun setShowSavedPositions(show: Boolean) {
        isVisible = show
        spheres.values.forEach { it.setEnabled(show) }
        labels.values.forEach { it.node.setEnabled(show) }
    }
}
