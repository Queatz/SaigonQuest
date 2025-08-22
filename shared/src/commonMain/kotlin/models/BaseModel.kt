package models

import kotlinx.serialization.Serializable

import kotlin.time.Instant

@Serializable
expect open class BaseModel() {
    var key: String
    var createdAt: Instant
}
