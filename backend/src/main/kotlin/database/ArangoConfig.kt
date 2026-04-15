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
    const val USER_REQUESTS_COLLECTION = "user_requests"
    // New asset item collections
    const val ITEM_ASSET_ITEMS_COLLECTION = "item_asset_items"
    const val SPRITESHEET_ASSET_ITEMS_COLLECTION = "spritesheet_asset_items"
    const val WORLD_ASSET_ITEMS_COLLECTION = "world_asset_items"
    const val OBJECT_ASSET_ITEMS_COLLECTION = "object_asset_items"
    const val AI_CHAT_SESSIONS_COLLECTION = "ai_chat_sessions"
    const val QUESTS_COLLECTION = "quests"
    const val QUEST_STATES_COLLECTION = "quest_states"
    const val PLAYER_QUEST_STATES_COLLECTION = "player_quest_states"
    const val PLAYER_QUEST_PROGRESS_COLLECTION = "player_quest_progress"
    const val USER_EMOJI_HISTORY_COLLECTION = "user_emoji_history"
    
    // World docs collection
    const val DOCS_COLLECTION = "docs"
    // Doc history collection
    const val DOC_HISTORY_COLLECTION = "doc_history"
    const val AI_PROMPT_HISTORY_COLLECTION = "ai_prompt_history"
    
    // Collection definitions + model registry (unified)
    private data class CollectionSpec(
        val options: CollectionCreateOptions,
        val model: Class<*>? = null,
    )

    private fun documentOptions(): CollectionCreateOptions =
        CollectionCreateOptions().type(CollectionType.DOCUMENT)

    private val registry: Map<String, CollectionSpec> = mapOf(
        WORLDS_COLLECTION to CollectionSpec(documentOptions(), models.World::class.java),
        USERS_COLLECTION to CollectionSpec(documentOptions(), models.User::class.java),
        OBJECTS_COLLECTION to CollectionSpec(documentOptions(), models.Object::class.java),
        CHAT_MESSAGES_COLLECTION to CollectionSpec(documentOptions(), models.ChatMessage::class.java),
        CHAT_READ_MARKERS_COLLECTION to CollectionSpec(documentOptions(), models.ChatReadMarker::class.java),
        CHAT_MESSAGE_PINS_COLLECTION to CollectionSpec(documentOptions(), models.ChatMessagePin::class.java),
        NOTIFICATIONS_COLLECTION to CollectionSpec(documentOptions(), models.Notification::class.java),
        FRIENDS_COLLECTION to CollectionSpec(documentOptions(), models.Friend::class.java),
        FRIEND_REQUESTS_COLLECTION to CollectionSpec(documentOptions(), models.FriendRequest::class.java),
        INVENTORIES_COLLECTION to CollectionSpec(documentOptions(), models.Inventory::class.java),
        INVENTORY_ITEMS_COLLECTION to CollectionSpec(documentOptions(), models.InventoryItem::class.java),
        ITEMS_COLLECTION to CollectionSpec(documentOptions(), models.Item::class.java),
        ASSETS_COLLECTION to CollectionSpec(documentOptions(), models.Asset::class.java),
        PUSH_SUBSCRIPTIONS_COLLECTION to CollectionSpec(documentOptions(), models.PushSubscription::class.java),
        USER_MEDIA_COLLECTION to CollectionSpec(documentOptions(), models.UserMedia::class.java),
        USER_BALANCES_COLLECTION to CollectionSpec(documentOptions(), models.UserBalance::class.java),
        USER_BALANCE_CHANGE_REQUESTS_COLLECTION to CollectionSpec(documentOptions(), models.UserBalanceChangeRequest::class.java),
        USER_BALANCE_HISTORY_COLLECTION to CollectionSpec(documentOptions(), models.UserBalanceHistory::class.java),
        USER_REQUESTS_COLLECTION to CollectionSpec(documentOptions(), models.UserRequest::class.java),
        ITEM_ASSET_ITEMS_COLLECTION to CollectionSpec(documentOptions(), models.ItemAssetItem::class.java),
        SPRITESHEET_ASSET_ITEMS_COLLECTION to CollectionSpec(documentOptions(), models.SpriteSheetAssetItem::class.java),
        WORLD_ASSET_ITEMS_COLLECTION to CollectionSpec(documentOptions(), models.WorldAssetItem::class.java),
        OBJECT_ASSET_ITEMS_COLLECTION to CollectionSpec(documentOptions(), models.ObjectAssetItem::class.java),
        AI_CHAT_SESSIONS_COLLECTION to CollectionSpec(documentOptions(), models.AiChatSession::class.java),
        QUESTS_COLLECTION to CollectionSpec(documentOptions(), models.Quest::class.java),
        QUEST_STATES_COLLECTION to CollectionSpec(documentOptions(), models.QuestState::class.java),
        PLAYER_QUEST_STATES_COLLECTION to CollectionSpec(documentOptions(), models.PlayerQuestState::class.java),
        PLAYER_QUEST_PROGRESS_COLLECTION to CollectionSpec(documentOptions(), models.PlayerQuestProgress::class.java),
        DOCS_COLLECTION to CollectionSpec(documentOptions(), models.Doc::class.java),
        DOC_HISTORY_COLLECTION to CollectionSpec(documentOptions(), models.DocHistory::class.java),
        AI_PROMPT_HISTORY_COLLECTION to CollectionSpec(documentOptions(), models.AiPromptHistory::class.java),
        USER_EMOJI_HISTORY_COLLECTION to CollectionSpec(documentOptions(), models.UserEmojiHistory::class.java),
    )

    fun initializeCollections(database: ArangoDatabase) {
        val existing = database.collections.map { it.name }
        registry.forEach { (name, spec) ->
            if (name !in existing) database.createCollection(name, spec.options)
        }
        // Ensure indices declared via annotations on models
        try {
            ensureAnnotatedIndexes(database)
        } catch (_: Throwable) {
            // Ignore index errors at startup
        }
    }

    private fun ensureAnnotatedIndexes(database: ArangoDatabase) {
        registry.forEach { (collectionName, spec) ->
            val clazz = spec.model ?: return@forEach
            try {
                ensureModelIndexes(database, collectionName, clazz)
            } catch (_: Throwable) {
                // Ignore per-model index errors at startup
            }
        }
    }

    private data class IndexedField(
        val name: String,
        val group: String,
        val order: Int,
        val unique: Boolean,
        val sparse: Boolean,
        val deduplicate: Boolean,
    )

    private fun ensureModelIndexes(
        database: ArangoDatabase,
        collectionName: String,
        modelClass: Class<*>,
    ) {
        val coll = database.collection(collectionName)
        val fields = modelClass.declaredFields.mapNotNull { f ->
            val ann = f.getAnnotation(models.annotations.Indexed::class.java) ?: return@mapNotNull null
            IndexedField(
                name = f.name,
                group = ann.group,
                order = ann.order,
                unique = ann.unique,
                sparse = ann.sparse,
                deduplicate = ann.deduplicate,
            )
        }
        // Single-field indices (no group)
        fields.filter { it.group.isBlank() }.forEach { sf ->
            val opts = PersistentIndexOptions()
                .unique(sf.unique)
                .sparse(sf.sparse)
                .deduplicate(sf.deduplicate)
            coll.ensurePersistentIndex(listOf(sf.name), opts)
        }
        // Grouped (composite) indices from field annotations
        fields.filter { it.group.isNotBlank() }
            .groupBy { it.group }
            .forEach { (_, groupFields) ->
                val ordered = groupFields.sortedBy { it.order }
                if (ordered.isEmpty()) return@forEach
                val first = ordered.first()
                val opts = PersistentIndexOptions()
                    .unique(first.unique)
                    .sparse(first.sparse)
                    .deduplicate(first.deduplicate)
                val fieldNames = ordered.map { it.name }
                coll.ensurePersistentIndex(fieldNames, opts)
            }
        // Class-level composite indices (CompositeIndex/CompositeIndexes)
        run {
            val composites = mutableListOf<models.annotations.CompositeIndex>()
            modelClass.getAnnotation(models.annotations.CompositeIndexes::class.java)?.let { composites.addAll(it.value) }
            modelClass.getAnnotation(models.annotations.CompositeIndex::class.java)?.let { composites.add(it) }
            composites.forEach { ci ->
                val opts = PersistentIndexOptions()
                    .unique(ci.unique)
                    .sparse(ci.sparse)
                    .deduplicate(ci.deduplicate)
                coll.ensurePersistentIndex(ci.fields.toList(), opts)
            }
        }
    }
}
