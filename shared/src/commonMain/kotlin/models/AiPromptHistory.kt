package models

import kotlinx.serialization.Serializable
import models.annotations.CompositeIndex
import models.annotations.CompositeIndexes
import models.annotations.Indexed
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Stores a single AI prompt initiated by a user.
 */
@CompositeIndexes(
    value = [
        CompositeIndex(fields = ["userId", "createdAt"]),
    ]
)
@Serializable
data class AiPromptHistory(
    @field:Indexed
    var userId: String = "",
    var prompt: String = "",
    var responsePreview: String = "",
    var docId: String? = null,
    var docLength: Int = 0,
    var caretStart: Int = 0,
    var caretEnd: Int = 0,
    // createdAt is inherited from BaseModel
) : BaseModel()
