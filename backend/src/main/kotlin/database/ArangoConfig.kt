@file:OptIn(ExperimentalTime::class)

package database

import com.arangodb.ArangoDB
import com.arangodb.ArangoDatabase
import com.arangodb.entity.CollectionType
import com.arangodb.model.CollectionCreateOptions
import com.arangodb.serde.jackson.internal.JacksonSerdeImpl
import com.arangodb.model.PersistentIndexOptions
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object ArangoConfig {
    private const val HOST = "localhost"
    private const val PORT = 8529
    private const val DB_NAME = "saigon_quest"
    private const val USERNAME = "saigonquest"
    private const val PASSWORD = "saigonquest" // Use environment variables in production
    
    // Custom serializer for kotlinx.datetime.Instant that extends StdSerializer<Instant>
    private class KotlinxInstantSerializer : StdSerializer<Instant>(Instant::class.java) {
        @Throws(IOException::class)
        override fun serialize(value: Instant, gen: JsonGenerator, provider: SerializerProvider) {
            // Convert to string and write as raw ISO string value
            gen.writeString(value.toString())
        }
    }
    
    // Custom deserializer for kotlinx.datetime.Instant that extends StdDeserializer<Instant>
    private class KotlinxInstantDeserializer : StdDeserializer<Instant>(Instant::class.java) {
        @Throws(IOException::class)
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant {
            // Get the current value as a string
            val text = p.valueAsString
            
            // Parse the string to Instant
            return Instant.parse(text)
        }
    }
    
    // Create a custom module for handling kotlinx.datetime.Instant
    private val kotlinxDateTimeModule = SimpleModule("KotlinxDateTimeModule").apply {
        addSerializer(Instant::class.java, KotlinxInstantSerializer())
        addDeserializer(Instant::class.java, KotlinxInstantDeserializer())
    }
    
    // Configure Jackson ObjectMapper with JSR310 module for handling Java 8 date/time types
    private val objectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(kotlinxDateTimeModule)
        registerKotlinModule()
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        // Be tolerant of older documents having extra fields
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES.also { feature ->
            disable(feature)
        }
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
        
        // Initialize collections
        CollectionsConfig.initializeCollections(database)
        
        database
    }
    
    fun shutdown() {
        arangoDB.shutdown()
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
        ASSETS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT)
        , PUSH_SUBSCRIPTIONS_COLLECTION to CollectionCreateOptions().type(CollectionType.DOCUMENT)
    )
    
    fun initializeCollections(database: ArangoDatabase) {
        val existingCollections = database.collections.map { it.name }
        
        collections.forEach { (collectionName, options) ->
            if (!existingCollections.contains(collectionName)) {
                database.createCollection(collectionName, options)
            }
        }

        // Ensure indexes for chat_message_pins collection
        try {
            val pins = database.collection(CHAT_MESSAGE_PINS_COLLECTION)
            // Unique to prevent duplicate pins per world/message
            try {
                pins.ensurePersistentIndex(listOf("worldId", "messageId"), PersistentIndexOptions().unique(true))
            } catch (_: Throwable) {}
            // For fast listing and sorting
            try {
                pins.ensurePersistentIndex(listOf("worldId", "pinnedAt"), PersistentIndexOptions().unique(false))
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }
}
