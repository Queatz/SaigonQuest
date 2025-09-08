package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import repository.UserRepository
import utils.json
import java.time.Year

/**
 * Runner for UserAge nodes
 *
 * Evaluates the connected user's age against optional min/max constraints and sets
 * eventData["outputProperty"] to PropertyKey.YES or PropertyKey.NO.
 */
class AgeNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("AGE_NODE_EXECUTE", "Executing UserAge node with data: ${node.data}", context.session)

        return try {
            // Parse node configuration
            val jsonData = node.data["data"] ?: "{}"
            val nodeData = json.decodeFromString<PropertyBasedNodeData>(jsonData)

            val minAgeRaw = nodeData.properties.find { it.key == PropertyKey.MIN_AGE }?.value?.trim().orEmpty()
            val maxAgeRaw = nodeData.properties.find { it.key == PropertyKey.MAX_AGE }?.value?.trim().orEmpty()

            val minAge = minAgeRaw.toIntOrNull()?.takeIf { it> 0 }
            val maxAge = maxAgeRaw.toIntOrNull()?.takeIf { it> 0 }

            WebSocketDebug.logMessage(
                "AGE_NODE_LIMITS",
                "Parsed minAge=$minAge, maxAge=$maxAge (raw: '$minAgeRaw', '$maxAgeRaw')",
                context.session
            )

            // Fetch user to compute age
            val user = try { UserRepository().getById(context.connection.userId) } catch (t: Throwable) {
                WebSocketDebug.logError("Failed to load user in UserAge node", t, context.session)
                null
            }
            val currentYear = Year.now().value
            val age: Int? = user?.yearOfBirth?.let { yob -> currentYear - yob }

            WebSocketDebug.logMessage("AGE_NODE_USER_AGE", "Computed user age=$age from yearOfBirth=${user?.yearOfBirth}", context.session)

            // Evaluate
            val qualifies: Boolean = when {
                // No constraints: always yes
                minAge == null && maxAge == null -> true
                // Unknown age but constraints exist: cannot qualify
                age == null -> false
                else -> {
                    // Normalize invalid ranges by swapping if needed
                    val (min, max) = if (minAge != null && maxAge != null && minAge > maxAge) {
                        maxAge to minAge
                    } else {
                        minAge to maxAge
                    }

                    if (min != null && age < min) false
                    else if (max != null && age > max) false
                    else true
                }
            }

            val output = if (qualifies) PropertyKey.YES else PropertyKey.NO
            WebSocketDebug.logMessage("AGE_NODE_RESULT", "Setting outputProperty to $output (qualifies=$qualifies)", context.session)

            eventData["outputProperty"] = output
            true
        } catch (t: Throwable) {
            WebSocketDebug.logError("Error executing UserAge node", t, context.session)
            false
        }
    }
}
