package main.components.tools

import androidx.compose.runtime.Composable
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.Translated
import models.LessonScene
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

@Composable
fun EditorBottomBar(
    darkMode: Boolean,
    isSaving: Boolean,
    isGeneratingRemaining: Boolean,
    currentScene: LessonScene,
    onSave: (() -> Unit) -> Unit,
    onPrint: () -> Unit,
    onGenerateRemaining: () -> Unit
) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex); gap(16.px); marginTop(32.px)
            position(org.jetbrains.compose.web.css.Position.Sticky); bottom(16.px)
            backgroundColor(if (darkMode) main.theme.Theme.colors.backgroundDark else main.theme.Theme.colors.background)
            padding(16.px)
            property("z-index", "100")
        }
    }) {
        DialogButton(
            isLoading = isSaving,
            onClick = {
                onSave {
                    // Handled in LessonEditorPage usually
                }
            },
        ) {
            Translated("Save lesson")
        }
        DialogButton(
            isLoading = isSaving,
            onClick = onPrint,
        ) {
            Translated("Print document")
        }

        val hasRemainingImages = currentScene.eventCards.any { it.image == null && it.text.isNotBlank() } ||
                currentScene.items.any { it.image == null && it.name.isNotBlank() } ||
                currentScene.referencePages.any { page -> page.rows.any { it.image == null && it.text.isNotBlank() } }

        if (hasRemainingImages) {
            DialogButton(
                style = DialogButtonStyle.Secondary,
                isLoading = isGeneratingRemaining,
                onClick = onGenerateRemaining,
            ) {
                Translated("Generate remaining images")
            }
        }
    }
}
