package main.components.nodeeditor

import androidx.compose.runtime.*
import main.api.QuestApi
import main.components.DialogSelect
import main.components.Translated
import models.Quest
import org.jetbrains.compose.web.dom.P

@Composable
fun QuestStepPropertySelector(
    questId: String,
    value: String,
    onInput: (String) -> Unit,
) {
    var quest by remember(questId) { mutableStateOf<Quest?>(null) }
    var isLoading by remember(questId) { mutableStateOf(questId.isNotEmpty()) }

    LaunchedEffect(questId) {
        if (questId.isEmpty() || questId == "Quest ID") {
            quest = null
            isLoading = false
            return@LaunchedEffect
        }
        try {
            quest = QuestApi.getQuest(questId)
        } catch (t: Throwable) {
            // Only log if it's not a 404, which is expected during editing if ID is being typed or invalid
            val is404 = (t as? models.ErrorResponse)?.code == 404
            if (!is404) {
                console.error("Failed to load quest for steps", t)
            }
            quest = null
        } finally {
            isLoading = false
        }
    }

    if (questId.isEmpty() || questId == "Quest ID") {
        P { Translated("Select a quest first") }
    } else if (isLoading) {
        P { Translated("Loading steps...") }
    } else {
        val q = quest
        if (q == null) {
            P { Translated("Quest not found") }
        } else {
            val options = q.steps.mapIndexed { index, step ->
                index.toString() to (step.title.ifBlank { "Step $index" })
            }
            
            DialogSelect(
                options = options,
                value = value,
                onInput = onInput,
                label = null
            )
        }
    }
}
