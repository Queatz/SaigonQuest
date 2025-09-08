package logic

import debug.WebSocketDebug
import models.Sex
import models.nodeeditor.NodeDataModel
import repository.UserRepository

/**
 * Runner for user sex nodes
 *
 * This node runner handles checking the user's sex and directing the flow accordingly.
 * It sets a buttonIndex in the eventData based on the user's sex:
 * - 0 for Boy
 * - 1 for Girl
 */
class SexNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val userRepository = UserRepository()

        WebSocketDebug.logMessage("SEX_NODE_EXECUTE", "Executing sex node with data: ${node.data}", context.session)

        // Get the user's sex from the repository
        val sex = try {
            userRepository.getById(context.connection.userId)?.sex
        } catch (t: Throwable) {
            WebSocketDebug.logError("Failed to get user for sex node", t, context.session)
            null
        }

        WebSocketDebug.logMessage("USER_SEX", "User sex: ${sex}", context.session)

        // Set the buttonIndex based on the sex
        val buttonIndex = when (sex) {
            Sex.Girl -> 1
            Sex.Boy -> 0
            null -> 0 // default
        }

        WebSocketDebug.logMessage(
            "SEX_BUTTON_INDEX",
            "Setting button index to $buttonIndex for sex: ${sex}",
            context.session
        )

        // Set the buttonIndex in the eventData
        eventData["buttonIndex"] = buttonIndex

        WebSocketDebug.logMessage("SEX_NODE_SUCCESS", "Sex node execution completed successfully", context.session)
        return true
    }
}
