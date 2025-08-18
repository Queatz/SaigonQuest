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
    private const val DB_NAME = "saigon_quest"
    private const val USERNAME = "saigonquest"
    private const val PASSWORD = "saigonquest" // Use env vars in production

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
    // Collection names
    const val WORLDS_COLLECTION = "worlds"
    const val USERS_COLLECTION = "users"
    const val OBJECTS_COLLECTION = "objects"
    const val CHAT_MESSAGES_COLLECTION = "chat_messages"
    const val CHAT_READ_MARKERS_COLLECTION = "chat_read_markers"
    const val CHAT_MESSAGE_PINS_COLLECTION = "chat_message_pins"
    const val NOTIFICATIONS_COLLECTION = "notifications"
    const val FRIENDS_COLLECTION = "friends"
    const val INVENTORIES_COLLECTION = "inventories"
    const val INVENTORY_ITEMS_COLLECTION = "inventory_items"
    const val ITEMS_COLLECTION = "items"
    const val ASSETS_COLLECTION = "assets"
    const val PUSH_SUBSCRIPTIONS_COLLECTION = "push_subscriptions"
    const val USER_MEDIA_COLLECTION = "user_media"
    const val USER_BALANCES_COLLECTION = "user_balances"
    const val USER_BALANCE_CHANGE_REQUESTS_COLLECTION = "user_balance_change_requests"
    const val USER_BALANCE_HISTORY_COLLECTION = "user_balance_history"
    
    // Collection definitions
    private val collections = mapOf(
        WORLDS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USERS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        OBJECTS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        CHAT_MESSAGES_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        CHAT_READ_MARKERS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        CHAT_MESSAGE_PINS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        NOTIFICATIONS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        FRIENDS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        INVENTORIES_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        INVENTORY_ITEMS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        ITEMS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        ASSETS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        PUSH_SUBSCRIPTIONS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USER_MEDIA_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USER_BALANCES_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USER_BALANCE_CHANGE_REQUESTS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USER_BALANCE_HISTORY_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT)
    )

    fun initializeCollections(database: ArangoDatabase) {
        val existing = database.collections.map { it.name }
        collections.forEach { (name, opts) -> if (name !in existing) database.createCollection(name, opts) }
    }
}
