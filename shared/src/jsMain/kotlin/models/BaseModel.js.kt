package models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class, ExperimentalTime::class)
actual open class BaseModel {
    @JsonNames("key", "_key")
    actual var key: String = Uuid.random().toString().replace("-", "")

    actual var createdAt: Instant = Clock.System.now()
}
