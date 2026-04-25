package main.components

import androidx.compose.runtime.*
import main.api.SkillApi
import models.Skill

@Composable
fun SkillSelector(
    currentId: String,
    onUpdate: (String) -> Unit,
    label: String? = null
) {
    var skills by remember { mutableStateOf<List<Skill>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            skills = SkillApi.getAllSkills()
        } catch (t: Throwable) {
            console.error("Failed to load skills for editor", t)
        } finally {
            isLoading = false
        }
    }

    val selectOptions = remember(skills, currentId) {
        val list = skills.map { it.key to it.title }
        if (currentId.isNotBlank() && skills.none { it.key == currentId }) {
            listOf(currentId to "Unknown skill ($currentId)") + list
        } else if (currentId.isBlank()) {
            listOf("" to "Select skill...") + list
        } else list
    }

    DialogSelect(
        value = currentId,
        options = selectOptions,
        onInput = onUpdate,
        label = label,
        enabled = !isLoading
    )
}
