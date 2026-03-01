package main.components.objects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.components.Translated
import main.theme.EditorStyles
import main.theme.Styles
import models.Object
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import Translations.str
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px

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
    isCreatePlacementActive: Boolean = false,
    isDialog: Boolean = false,
    onCreateObject: () -> Unit = {},
    onRemove: (String) -> Unit,
    onLocate: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    onDuplicate: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredObjects = if (searchQuery.isBlank()) {
        objects
    } else {
        objects.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.status.contains(searchQuery, ignoreCase = true)
        }
    }

    // Container for the content
    Div(
        attrs = {
            classes(EditorStyles.worldObjectsContainer)

            if (isDialog) {
                style {
                    padding(16.px)
                }
            }
        }
    ) {
        // Create object button
        Button(
            attrs = {
                classes(EditorStyles.createObjectButton)
                onClick { onCreateObject() }
            }
        ) {
            if (isCreatePlacementActive) {
                Translated("Cancel")
            } else {
                Translated("Create object")
            }
        }

        // Search input
        if (objects.size > 5) {
            TextInput(
                value = searchQuery,
                attrs = {
                    classes(Styles.inputField, EditorStyles.worldObjectsSearchInput)
                    placeholder(str("Search objects"))
                    onInput { searchQuery = it.value }
                }
            )
        }

        // Loading or error message
        if (isLoading) {
            P(
                attrs = {
                    classes(EditorStyles.loadingMessage)
                }
            ) {
                Translated("Loading objects...")
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
                Translated("No objects in this world yet.")
            }
        } else if (filteredObjects.isEmpty()) {
            P(
                attrs = {
                    classes(EditorStyles.emptyMessage)
                }
            ) {
                Translated("No results")
            }
        } else {
            // List of objects
            Div(
                attrs = {
                    classes(EditorStyles.objectsListContainer)
                }
            ) {
                // Display objects
                filteredObjects.forEach { obj ->
                    ObjectItem(
                        obj = obj,
                        onRemove = onRemove,
                        onLocate = onLocate,
                        onEdit = onEdit,
                        onDuplicate = onDuplicate
                    )
                }
            }
        }
    }
}

@Composable
fun ObjectItem(
    obj: Object,
    onRemove: (String) -> Unit,
    onLocate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit
) {
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
            if (obj.status.isNotBlank()) {
                Span(
                    attrs = {
                        classes(Styles.userStatusText)
                    }
                ) {
                    Text(" • ")
                    Text(obj.status)
                }
            }
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

            // Duplicate button
            Button(
                attrs = {
                    classes(EditorStyles.editButton)
                    onClick { onDuplicate(obj.key) }
                }
            ) {
                Text("Duplicate")
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
