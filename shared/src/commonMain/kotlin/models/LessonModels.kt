package models

import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class LessonScene(
    var title: String = "",
    var sceneImage: String? = null,
    var scenePrompt: String = "",
    var eventCards: List<EventCard> = emptyList(),
    var items: List<LessonItem> = emptyList(),
    var referencePages: List<ReferencePage> = emptyList(),
    var createdBy: String = "",
) : BaseModel()

fun LessonScene.update(block: LessonScene.() -> Unit): LessonScene {
    return this.copy().also {
        it.key = this.key
        it.createdAt = this.createdAt
        it.block()
    }
}

@Serializable
data class EventCard(
    val id: String = "",
    var image: String? = null,
    var text: String = "",
)

@Serializable
data class LessonItem(
    val id: String = "",
    var image: String? = null,
    var name: String = "",
)

@Serializable
data class ReferenceRow(
    val id: String = "",
    var image: String? = null,
    var text: String = "",
)

@Serializable
data class ReferencePage(
    val id: String = "",
    var title: String = "",
    var rows: List<ReferenceRow> = emptyList(),
)
