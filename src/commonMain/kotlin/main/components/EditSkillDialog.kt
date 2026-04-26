package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.SkillApi
import main.theme.Styles
import models.Skill
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun EditSkillDialog(
    skill: Skill,
    onClose: () -> Unit,
    onSaved: (Skill) -> Unit,
    onDeleted: () -> Unit,
) {
    var title by remember { mutableStateOf(skill.title) }
    var description by remember { mutableStateOf(skill.description) }
    var imageUrl by remember { mutableStateOf(skill.imageUrl) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun save() {
        if (title.isBlank()) {
            errorMessage = "Title is required"
            return
        }

        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                val updatedSkill = skill.copy(
                    title = title,
                    description = description,
                    imageUrl = imageUrl,
                )
                val saved = SkillApi.updateSkill(skill.key, updatedSkill)
                onSaved(saved)
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Failed to update skill"
            } finally {
                isSaving = false
            }
        }
    }

    fun delete() {
        isDeleting = true
        errorMessage = null
        scope.launch {
            try {
                SkillApi.deleteSkill(skill.key)
                onDeleted()
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Failed to delete skill"
            } finally {
                isDeleting = false
                showDeleteConfirm = false
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Edit skill"),
        content = {
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(16.px)
                }
            }) {
                if (errorMessage != null) {
                    P { Text(errorMessage!!) }
                }

                DialogInput(
                    label = translated("Skill title"),
                    value = title,
                    onInput = { title = it },
                )

                DialogInput(
                    label = translated("Skill description"),
                    value = description,
                    onInput = { description = it },
                    multiline = true,
                )

                MediaSelector(
                    url = imageUrl,
                    type = models.MediaType.Image,
                    onSelect = { imageUrl = it },
                    onClear = { imageUrl = "" },
                )
            }
        },
        actions = {
            DialogButton(
                onClick = { save() },
                style = DialogButtonStyle.Primary,
                isDisabled = isSaving || isDeleting,
                attrs = { style { width(100.percent) } }
            ) {
                if (isSaving) Translated("Saving...") else Translated("Save")
            }

            DialogButton(
                onClick = onClose,
                style = DialogButtonStyle.Secondary,
                isDisabled = isSaving || isDeleting,
                attrs = { style { width(100.percent) } }
            ) { Translated("Cancel") }

            DialogButton(
                onClick = { showDeleteConfirm = true },
                style = DialogButtonStyle.Secondary,
                isDisabled = isSaving || isDeleting,
                attrs = { style { width(100.percent) } }
            ) { Translated("Delete") }
        }
    )

    if (showDeleteConfirm) {
        BasicConfirmDialog(
            title = translated("Delete skill"),
            message = translated("Are you sure you want to delete this skill?"),
            onConfirm = { delete() },
            onCancel = { showDeleteConfirm = false }
        )
    }
}
