package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.components.*
import main.api.SkillApi
import main.theme.Styles
import models.Skill
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.css.*

@Composable
fun AdminSkillsDialog(
    onClose: () -> Unit,
) {
    var skills by remember { mutableStateOf<List<Skill>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateSkillDialog by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var skillForUsers by remember { mutableStateOf<Skill?>(null) }

    val scope = rememberCoroutineScope()

    fun loadSkills() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                skills = SkillApi.getAllSkills()
            } catch (t: Throwable) {
                console.error("Failed to load skills", t)
                errorMessage = t.message ?: "Failed to load skills"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadSkills() }

    val headerActions: @Composable () -> Unit = {
        DialogButton(
            style = DialogButtonStyle.Primary,
            onClick = { showCreateSkillDialog = true }
        ) { Translated("New") }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Skills"),
        headerActions = headerActions,
        content = {
            if (errorMessage != null) {
                P { Text(errorMessage!!) }
            }
            if (isLoading) {
                P { Translated("Loading...") }
            } else if (skills.isEmpty()) {
                P { Translated("No skills found") }
            } else {
                // Header row
                Div(attrs = {
                    style {
                        display(DisplayStyle.Grid)
                        gridTemplateColumns("64px 1fr 2fr 150px")
                        gap(16.px)
                        padding(8.px)
                        property("border-bottom", "1px solid rgba(255,255,255,0.1)")
                        fontWeight("bold")
                    }
                }) {
                    Span { Translated("Image") }
                    Span { Translated("Title") }
                    Span { Translated("Description") }
                    Span { Translated("Actions") }
                }

                // List rows
                for (skill in skills) {
                    Paper(attrs = {
                        style {
                            display(DisplayStyle.Grid)
                            gridTemplateColumns("64px 1fr 2fr 150px")
                            gap(16.px)
                            marginBottom(8.px)
                            alignItems(AlignItems.Center)
                        }
                    }) {
                        // image
                        Div {
                            if (skill.imageUrl.isNotBlank()) {
                                Img(src = skill.imageUrl, attrs = {
                                    style {
                                        width(48.px)
                                        height(48.px)
                                        property("object-fit", "cover")
                                        borderRadius(8.px)
                                    }
                                })
                            }
                        }
                        Span { Text(skill.title.ifBlank { "(untitled)" }) }
                        Span { 
                            Text(
                                if (skill.description.length > 100) 
                                    skill.description.take(97) + "..." 
                                else skill.description
                            ) 
                        }
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                gap(8.px)
                                flexDirection(FlexDirection.Column)
                            }
                        }) {
                            DialogButton(
                                style = DialogButtonStyle.Primary,
                                onClick = { editingSkill = skill }
                            ) { Translated("Edit") }
                            
                            DialogButton(
                                style = DialogButtonStyle.Secondary,
                                onClick = { skillForUsers = skill }
                            ) { Translated("Users") }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )

    if (showCreateSkillDialog) {
        CreateSkillDialog(
            onClose = { showCreateSkillDialog = false },
            onSkillCreated = {
                showCreateSkillDialog = false
                loadSkills()
            },
        )
    }

    editingSkill?.let { skill ->
        EditSkillDialog(
            skill = skill,
            onClose = { editingSkill = null },
            onSaved = { updated ->
                editingSkill = null
                skills = skills.map { if (it.key == updated.key) updated else it }
            },
            onDeleted = {
                editingSkill = null
                loadSkills()
            }
        )
    }
    
    skillForUsers?.let { skill ->
        SkillUsersDialog(
            skill = skill,
            onClose = { skillForUsers = null }
        )
    }
}
