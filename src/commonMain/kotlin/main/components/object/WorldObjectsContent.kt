package main.components.`object`

import androidx.compose.runtime.Composable
import main.theme.EditorStyles
import models.Object
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Content for the World tab - lists all objects in the world with locate, edit, and remove functionality.
 * 
 * @param objects List of objects to display
 * @param isLoading Whether the objects are currently loading
 * @param errorMessage Error message to display if there was an error loading objects
 * @param onRemove Callback when an object is removed
 * @param onLocate Callback when an object should be located in the 3D view
 * @param onEdit Callback when an object should be edited
 */
@Composable
fun WorldObjectsContent(
    objects: List<Object>,
    isLoading: Boolean,
    errorMessage: String,
    onCreateObject: () -> Unit = {},
    onRemove: (String) -> Unit,
    onLocate: (String) -> Unit = {},
    onEdit: (String) -> Unit = {}
) {
    // Container for the content
    Div(
        attrs = {
            classes(EditorStyles.worldObjectsContainer)
        }
    ) {
        // Create object button
        Button(
            attrs = {
                classes(EditorStyles.createObjectButton)
                onClick { onCreateObject() }
            }
        ) {
            Text("Create Object")
        }
        // Loading or error message
        if (isLoading) {
            P(
                attrs = {
                    classes(EditorStyles.loadingMessage)
                }
            ) {
                Text("Loading objects...")
            }
        } else if (errorMessage.isNotEmpty()) {
            P(
                attrs = {
                    classes(EditorStyles.errorMessage)
                }
            ) {
                Text(errorMessage)
            }
        } else if (objects.isEmpty()) {
            P(
                attrs = {
                    classes(EditorStyles.emptyMessage)
                }
            ) {
                Text("No objects in this world yet.")
            }
        } else {
            // List of objects
            Div(
                attrs = {
                    classes(EditorStyles.objectsListContainer)
                }
            ) {
                // Display objects
                objects.forEach { obj ->
                    Div(
                        attrs = {
                            classes(EditorStyles.objectItem)
                        }
                    ) {
                        // Object title
                        Span(
                            attrs = {
                                classes(EditorStyles.objectTitle)
                            }
                        ) {
                            Text(obj.title.takeIf { it.isNotEmpty() } ?: "Untitled")
                        }
                        
                        // Action buttons container
                        Div(
                            attrs = {
                                classes(EditorStyles.actionButtonsContainer)
                            }
                        ) {
                            // Locate button
                            Button(
                                attrs = {
                                    classes(EditorStyles.locateButton)
                                    onClick { onLocate(obj.key) }
                                }
                            ) {
                                Text("Locate")
                            }
                            
                            // Edit button
                            Button(
                                attrs = {
                                    classes(EditorStyles.editButton)
                                    onClick { onEdit(obj.key) }
                                }
                            ) {
                                Text("Edit")
                            }
                            
                            // Remove button
                            Button(
                                attrs = {
                                    classes(EditorStyles.removeButton)
                                    onClick { onRemove(obj.key) }
                                }
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
