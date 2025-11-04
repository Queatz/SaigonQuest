package database

import com.arangodb.ArangoDB
import com.arangodb.ArangoDatabase
import com.arangodb.entity.CollectionType
import com.arangodb.model.CollectionCreateOptions
import com.arangodb.model.PersistentIndexOptions
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
import kotlin.time.Instant

object ArangoConfig {
    private const val HOST = "localhost"
    private const val PORT = 8529
    private const val DB_NAME = "saigon_quest"
    private const val USERNAME = "saigonquest"
    private const val PASSWORD = "saigonquest" // Use env vars in production

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
        .build().also {
            // Register polymorphic handling for chat attachments using discriminator "type"
            it.addMixIn(models.chat.Attachment::class.java, serialization.AttachmentMixin::class.java)
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
    const val FRIEND_REQUESTS_COLLECTION = "friend_requests"
    const val INVENTORIES_COLLECTION = "inventories"
    const val INVENTORY_ITEMS_COLLECTION = "inventory_items"
    const val ITEMS_COLLECTION = "items"
    const val ASSETS_COLLECTION = "assets"
    const val PUSH_SUBSCRIPTIONS_COLLECTION = "push_subscriptions"
    const val USER_MEDIA_COLLECTION = "user_media"
    const val USER_BALANCES_COLLECTION = "user_balances"
    const val USER_BALANCE_CHANGE_REQUESTS_COLLECTION = "user_balance_change_requests"
    const val USER_BALANCE_HISTORY_COLLECTION = "user_balance_history"
    // New asset item collections
    const val ITEM_ASSET_ITEMS_COLLECTION = "item_asset_items"
    const val SPRITESHEET_ASSET_ITEMS_COLLECTION = "spritesheet_asset_items"
    const val WORLD_ASSET_ITEMS_COLLECTION = "world_asset_items"
    const val OBJECT_ASSET_ITEMS_COLLECTION = "object_asset_items"
    
    // World docs collection
    const val DOCS_COLLECTION = "docs"
    
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
        FRIEND_REQUESTS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        INVENTORIES_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        INVENTORY_ITEMS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        ITEMS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        ASSETS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        PUSH_SUBSCRIPTIONS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USER_MEDIA_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USER_BALANCES_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USER_BALANCE_CHANGE_REQUESTS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        USER_BALANCE_HISTORY_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        ITEM_ASSET_ITEMS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        SPRITESHEET_ASSET_ITEMS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        WORLD_ASSET_ITEMS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        OBJECT_ASSET_ITEMS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT),
        DOCS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT)
    )

    fun initializeCollections(database: ArangoDatabase) {
        val existing = database.collections.map { it.name }
        collections.forEach { (name, opts) ->
            if (name !in existing) database.createCollection(name, opts)
        }
        // Ensure indices for docs collection
        try {
            val coll = database.collection(DOCS_COLLECTION)
            coll.ensurePersistentIndex(listOf("worldId", "parentId", "orderIndex"), PersistentIndexOptions())
            coll.ensurePersistentIndex(listOf("worldId", "title"), PersistentIndexOptions())
            coll.ensurePersistentIndex(listOf("updatedAt"), PersistentIndexOptions())
        } catch (_: Throwable) {
            // Ignore index errors at startup
        }
    }
}
