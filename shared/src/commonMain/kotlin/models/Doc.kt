package models

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import models.annotations.CompositeIndex
import models.annotations.CompositeIndexes
import models.annotations.Indexed

@CompositeIndexes(
    value = [
        CompositeIndex(fields = ["worldId", "parentId", "orderIndex"]),
        CompositeIndex(fields = ["worldId", "title"]),
    ]
)
@Serializable
data class Doc(
    var worldId: String = "",
    var title: String = "",
    var content: String = "",
    var parentId: String? = null,
    var orderIndex: Double = 0.0,
    var createdBy: String = "",
    var version: Int = 0,
    @field:Indexed
    var updatedAt: Instant = Clock.System.now(),
    var permissions: DocPermissions = DocPermissions(),
) : BaseModel()

@Serializable
data class DocPermissions(
    val worldOwnerCanEdit: Boolean = true,
    val worldUsersCanView: Boolean = true,
    val worldUsersCanEdit: Boolean = false,
)

@Serializable
data class DocTree(
    val doc: Doc,
    val children: List<DocTree> = emptyList(),
)

@Serializable
enum class DocAccessLevel {
    NO_ACCESS,
    VIEWER,
    EDITOR,
    OWNER,
}
