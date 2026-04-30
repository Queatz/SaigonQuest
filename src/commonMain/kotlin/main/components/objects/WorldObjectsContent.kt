package main.components.objects

import Translations.str
import androidx.compose.runtime.*
import main.components.*
import main.components.Paper
import main.components.common.Spinner
import main.theme.EditorStyles
import main.theme.Styles
import models.Object
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
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

            if (!isDialog) {
                style {
                    padding(16.px)
                }
            }
        }
    ) {
        // Create object button
        DialogButton(
            onClick = { onCreateObject() },
            style = DialogButtonStyle.Primary,
            attrs = {
                classes(EditorStyles.createObjectButton)
            }
        ) {
            if (isCreatePlacementActive) {
                Translated("Cancel")
            } else {
                Translated("Create object")
            }
        }

        if (objects.size > 5) {
            DialogInput(
                value = searchQuery,
                onInput = { searchQuery = it },
                placeholder = str("Search objects"),
                containerAttrs = {
                    classes(EditorStyles.worldObjectsSearchInput)
                }
            )
        }

        // Loading or error message
        if (isLoading) {
            Spinner(message = translated("Loading objects..."))
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
    Paper(
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
            if (obj.title.isNotEmpty()) {
                Text(obj.title)
            } else {
                Translated("Untitled")
            }
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
            DialogButton(
                onClick = { onLocate(obj.key) },
                style = DialogButtonStyle.Primary,
            ) {
                Translated("Locate")
            }

            // Edit button
            DialogButton(
                onClick = { onEdit(obj.key) },
                style = DialogButtonStyle.Secondary,
            ) {
                Translated("Edit")
            }

            // Duplicate button
            DialogButton(
                onClick = { onDuplicate(obj.key) },
                style = DialogButtonStyle.Secondary,
            ) {
                Translated("Duplicate")
            }

            // Remove button
            DialogButton(
                onClick = { onRemove(obj.key) },
                style = DialogButtonStyle.Danger,
            ) {
                Translated("Remove")
            }
        }
    }
}
