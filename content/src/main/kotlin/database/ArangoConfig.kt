package database

import com.arangodb.ArangoDB
import com.arangodb.ArangoDatabase
import com.arangodb.entity.CollectionType
import com.arangodb.model.CollectionCreateOptions
import com.arangodb.serde.jackson.internal.JacksonSerdeImpl
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import db.StableNamingStrategy
import kotlin.time.Instant

object ArangoConfig {
    private const val HOST = "localhost"
    private const val PORT = 8529
    private const val DB_NAME = "saigon_quest_uploads"
    private const val USERNAME = "saigonquest"
    private const val PASSWORD = "saigonquest" // Use environment variables in production

    // Support kotlinx.datetime.Instant used in BaseModel.createdAt
    private class KotlinxInstantSerializer : StdSerializer<Instant>(Instant::class.java) {
        override fun serialize(value: Instant, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    private class KotlinxInstantDeserializer : StdDeserializer<Instant>(Instant::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant {
            val text = p.valueAsString
            return Instant.parse(text)
        }
    }

    private val kotlinxDateTimeModule = SimpleModule("KotlinxDateTimeModule").apply {
        addSerializer(Instant::class.java, KotlinxInstantSerializer())
        addDeserializer(Instant::class.java, KotlinxInstantDeserializer())
    }

    private val objectMapper = JsonMapper.builder()
        .addModule(JavaTimeModule())
        .addModule(kotlinxDateTimeModule)
        .addModule(kotlinModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.AUTO_DETECT_GETTERS)
        .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
        .disable(MapperFeature.AUTO_DETECT_SETTERS)
        .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .visibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
        .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .build()

    private val jacksonSerde = JacksonSerdeImpl(objectMapper)

    private val arangoDB = ArangoDB.Builder()
        .host(HOST, PORT)
        .user(USERNAME)
        .password(PASSWORD)
        .serde(jacksonSerde)
        .build()

    val db: ArangoDatabase by lazy {
        val database = arangoDB.db(DB_NAME)
        CollectionsConfig.initializeCollections(database)
        database
    }
}

object CollectionsConfig {
    const val CONTENT_UPLOADS_COLLECTION = "content_uploads"

    private val collections = mapOf(
        CONTENT_UPLOADS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT)
    )

    fun initializeCollections(database: ArangoDatabase) {
        val existing = database.collections.map { it.name }
        collections.forEach { (name, opts) -> if (name !in existing) database.createCollection(name, opts) }
    }
}
