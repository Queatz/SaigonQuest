package models

import com.arangodb.serde.jackson.Key
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.UUID
import kotlin.time.Clock

import kotlin.time.Instant

@Serializable
@OptIn(ExperimentalSerializationApi::class)
actual open class BaseModel {
    @Key
    @JsonNames("key", "_key")
    @JsonProperty("_key")
    @JsonAlias("key")
    actual var key: String = UUID.randomUUID().toString().replace("-", "")

    actual var createdAt: Instant = Clock.System.now()
}
