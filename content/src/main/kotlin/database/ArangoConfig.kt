@file:OptIn(kotlin.time.ExperimentalTime::class)

package database

import com.arangodb.ArangoDB
import com.arangodb.ArangoDatabase
import com.arangodb.entity.CollectionType
import com.arangodb.model.CollectionCreateOptions
import com.arangodb.serde.jackson.internal.JacksonSerdeImpl
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object ArangoConfig {
    private const val HOST = "localhost"
    private const val PORT = 8529
    private const val DB_NAME = "saigon_quest_uploads"
    private const val USERNAME = "saigonquest"
    private const val PASSWORD = "saigonquest" // Use environment variables in production

    // Support kotlinx.datetime.Instant used in BaseModel.createdAt
    private class KotlinxInstantSerializer : com.fasterxml.jackson.databind.ser.std.StdSerializer<kotlin.time.Instant>(kotlin.time.Instant::class.java) {
        override fun serialize(value: kotlin.time.Instant, gen: com.fasterxml.jackson.core.JsonGenerator, provider: com.fasterxml.jackson.databind.SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    private class KotlinxInstantDeserializer : com.fasterxml.jackson.databind.deser.std.StdDeserializer<kotlin.time.Instant>(kotlin.time.Instant::class.java) {
        override fun deserialize(p: com.fasterxml.jackson.core.JsonParser, ctxt: com.fasterxml.jackson.databind.DeserializationContext): kotlin.time.Instant {
            val text = p.valueAsString
            return kotlin.time.Instant.parse(text)
        }
    }

    private val kotlinxDateTimeModule = com.fasterxml.jackson.databind.module.SimpleModule("KotlinxDateTimeModule").apply {
        addSerializer(kotlin.time.Instant::class.java, KotlinxInstantSerializer())
        addDeserializer(kotlin.time.Instant::class.java, KotlinxInstantDeserializer())
    }

    private val objectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinxDateTimeModule)
        registerKotlinModule()
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES.also { feature -> disable(feature) }
    }

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
