package main.components

import androidx.compose.runtime.Composable

/**
 * A responsive world edit component that adapts between a dialog (mobile) and a side panel (desktop).
 * 
 * @param currentWorld The world being edited, or null if creating a new world
 * @param isMobile Whether the current device is mobile
 * @param onClose Callback when the world edit is closed
 * @param onSave Callback when a world is saved (created or updated)
 */
@Composable
fun WorldEdit(
    currentWorld: models.World? = null,
    isMobile: Boolean,
    onClose: () -> Unit,
    onSave: (models.World) -> Unit
) {
    ResponsivePanel(
        title = if (currentWorld != null) translated("Edit world") else translated("Create world"),
        isMobile = isMobile,
        onClose = onClose
    ) {
        WorldEditContent(
            currentWorld = currentWorld,
            onClose = onClose,
            onSave = onSave
        )
    }
}
