package models

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@OptIn(ExperimentalTime::class)
expect open class BaseModel() {
    var key: String
    var createdAt: Instant
}
