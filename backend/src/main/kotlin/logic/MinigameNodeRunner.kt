package logic

import debug.WebSocketDebug
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import models.*
import models.nodeeditor.*
import routes.WebSocketMessage
import utils.json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Runner for minigame nodes
 */
class MinigameNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    companion object {
        // Map to track pending minigame results
        private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<MinigameResult>>()
        private const val MINIGAME_TIMEOUT = 1800L // 30 minutes
        
        fun processResult(result: MinigameResult): Boolean {
            val minigameId = result.minigameId
            val deferred = pendingResults[minigameId]
            return if (deferred != null) {
                deferred.complete(result)
                true
            } else {
                false
            }
        }
    }

    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        // Parse property-based data
        val jsonData = node.data["data"] ?: "{}"
        val nodeData = try {
            json.decodeFromString<PropertyBasedNodeData>(jsonData)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse minigame node data", e, context.session)
            return false
        }

        fun inKey(key: PropertyKey) = "${node.id}_in_${key.name.lowercase()}"
        fun outKey(key: PropertyKey) = "${node.id}_out_${key.name.lowercase()}"
        
        fun getInput(key: PropertyKey): String? =
            (eventData[inKey(key)] as? String)?.takeIf { it.isNotBlank() }
                ?: nodeData.properties.find { it.key == key }?.value?.takeIf { it.isNotBlank() }

        val typeStr = getInput(PropertyKey.MINIGAME_TYPE) ?: "Flashcards"
        val configJson = getInput(PropertyKey.CONFIG_JSON) ?: "[]"
        val title = getInput(PropertyKey.TITLE) ?: node.label
        val timed = getInput(PropertyKey.TIMED)?.toBooleanStrictOrNull() ?: true
        
        val minigameType = try {
            MinigameType.valueOf(typeStr)
        } catch (e: Throwable) {
            MinigameType.Flashcards
        }

        val items = try {
            json.decodeFromString<List<MinigameItem>>(configJson)
        } catch (e: Throwable) {
            WebSocketDebug.logError("Failed to parse minigame items JSON", e, context.session)
            emptyList()
        }

        val minigameId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<MinigameResult>()
        pendingResults[minigameId] = deferred

        val config = MinigameConfig(
            minigameId = minigameId,
            type = minigameType,
            title = title,
            items = items,
            timed = timed
        )

        val message = WebSocketMessage(
            action = WebSocketAction.MINIGAME_PROMPT,
            data = json.encodeToString(config)
        )

        WebSocketDebug.logMessage("MINIGAME_PROMPT_SEND", "minigameId=$minigameId type=$minigameType", context.session)
        context.connection.session.send(json.encodeToString(message))

        try {
            val result = withTimeoutOrNull(MINIGAME_TIMEOUT.seconds) {
                deferred.await()
            }

            if (result != null) {
                WebSocketDebug.logMessage("MINIGAME_RESULT_RECEIVED", "minigameId=$minigameId reflex=${result.reflex} accuracy=${result.accuracy}", context.session)
                eventData[outKey(PropertyKey.REFLEX)] = result.reflex
                eventData[outKey(PropertyKey.ACCURACY)] = result.accuracy
                
                // Trigger the 'Complete' output port
                context.logicRunner?.triggerBranch(node.id, outKey(PropertyKey.COMPLETE), eventData)
                return true
            } else {
                WebSocketDebug.logMessage("MINIGAME_TIMEOUT", "minigameId=$minigameId", context.session)
                return false
            }
        } finally {
            pendingResults.remove(minigameId)
        }
    }
}
