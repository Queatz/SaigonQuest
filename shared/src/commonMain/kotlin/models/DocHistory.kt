package models

import kotlinx.serialization.Serializable
import models.annotations.CompositeIndex
import models.annotations.CompositeIndexes
import models.annotations.Indexed
import kotlin.time.Clock
import kotlin.time.Instant

@CompositeIndexes(
    value = [
        CompositeIndex(fields = ["docId", "createdAt"]),
    ]
)
@Serializable
data class DocHistory(
    var docId: String = "",
    var content: String = "",
    var patch: String = "",
    var userIds: List<String> = emptyList(),
) : BaseModel()
