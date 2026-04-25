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
fun CreateSkillDialog(
    onClose: () -> Unit,
    onSkillCreated: (Skill) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                val newSkill = Skill(
                    title = title,
                    description = description,
                    imageUrl = imageUrl,
                )
                val created = SkillApi.createSkill(newSkill)
                onSkillCreated(created)
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Failed to create skill"
            } finally {
                isSaving = false
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Create skill"),
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
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(8.px)
                    width(100.percent)
                }
            }) {
                DialogButton(
                    onClick = { save() },
                    style = DialogButtonStyle.Primary,
                    isDisabled = isSaving,
                    attrs = { style { width(100.percent) } }
                ) {
                    if (isSaving) Translated("Saving...") else Translated("Create")
                }

                DialogButton(
                    onClick = onClose,
                    style = DialogButtonStyle.Secondary,
                    isDisabled = isSaving,
                    attrs = { style { width(100.percent) } }
                ) { Translated("Cancel") }
            }
        }
    )
}
