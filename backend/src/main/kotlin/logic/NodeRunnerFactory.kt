package logic

import debug.WebSocketDebug
import models.NodeId
import models.nodeeditor.NodeDataModel

/**
 * Factory for creating node runners
 */
object NodeRunnerFactory {
    /**
     * Get the appropriate node runner for a node
     *
     * @param node The node to get a runner for
     * @param context The runner context
     * @return The node runner
     */
    fun getNodeRunner(node: NodeDataModel, context: NodeRunnerContext): NodeRunner {
        WebSocketDebug.logMessage(
            "GET_NODE_RUNNER",
            "Finding runner for node category: ${node.nodeCategory}, id: ${node.nodeId}, node id: ${node.id}",
            context.session
        )

        return when (node.nodeId) {
            NodeId.Teleport -> {
                WebSocketDebug.logMessage(
                    "TELEPORT_RUNNER_SELECTED",
                    "Selected TeleportNodeRunner for node: ${node.id}",
                    context.session
                )
                TeleportNodeRunner(context)
            }

            NodeId.Dialogue -> {
                WebSocketDebug.logMessage(
                    "DIALOGUE_RUNNER_SELECTED",
                    "Selected DialogueNodeRunner for node: ${node.id}",
                    context.session
                )
                DialogueNodeRunner(context)
            }

            NodeId.UserLanguage -> {
                WebSocketDebug.logMessage(
                    "LANGUAGE_RUNNER_SELECTED",
                    "Selected LanguageNodeRunner for node: ${node.id}",
                    context.session
                )
                LanguageNodeRunner(context)
            }

            NodeId.UserSex -> {
                WebSocketDebug.logMessage(
                    "SEX_RUNNER_SELECTED",
                    "Selected SexNodeRunner for node: ${node.id}",
                    context.session
                )
                SexNodeRunner(context)
            }

            NodeId.HasItem -> {
                WebSocketDebug.logMessage(
                    "HAS_ITEM_RUNNER_SELECTED",
                    "Selected HasItemNodeRunner for node: ${node.id}",
                    context.session
                )
                HasItemNodeRunner(context)
            }
            
            NodeId.Delay -> {
                WebSocketDebug.logMessage(
                    "DELAY_RUNNER_SELECTED",
                    "Selected DelayNodeRunner for node: ${node.id}",
                    context.session
                )
                DelayNodeRunner(context)
            }

            NodeId.OpenUrl -> {
                WebSocketDebug.logMessage(
                    "OPEN_URL_RUNNER_SELECTED",
                    "Selected OpenUrlNodeRunner for node: ${node.id}",
                    context.session
                )
                OpenUrlNodeRunner(context)
            }

            NodeId.OpenDoc -> {
                WebSocketDebug.logMessage(
                    "OPEN_DOC_RUNNER_SELECTED",
                    "Selected OpenDocNodeRunner for node: ${node.id}",
                    context.session
                )
                OpenDocNodeRunner(context)
            }
            
            NodeId.ObjectNotification -> {
                WebSocketDebug.logMessage(
                    "OBJECT_NOTIFICATION_RUNNER_SELECTED",
                    "Selected ObjectNotificationNodeRunner for node: ${node.id}",
                    context.session
                )
                ObjectNotificationNodeRunner(context)
            }
            NodeId.ObjectOwnerNotification -> {
                WebSocketDebug.logMessage(
                    "OBJECT_OWNER_NOTIFICATION_RUNNER_SELECTED",
                    "Selected ObjectOwnerNotificationNodeRunner for node: ${node.id}",
                    context.session
                )
                ObjectOwnerNotificationNodeRunner(context)
            }
            NodeId.TakeItems -> {
                WebSocketDebug.logMessage(
                    "TAKE_ITEMS_RUNNER_SELECTED",
                    "Selected TakeItemsNodeRunner for node: ${node.id}",
                    context.session
                )
                TakeItemsNodeRunner(context)
            }
            NodeId.GiveItems -> {
                WebSocketDebug.logMessage(
                    "GIVE_ITEMS_RUNNER_SELECTED",
                    "Selected GiveItemsNodeRunner for node: ${node.id}",
                    context.session
                )
                GiveItemsNodeRunner(context)
            }
            NodeId.UserAge -> {
                WebSocketDebug.logMessage(
                    "AGE_RUNNER_SELECTED",
                    "Selected AgeNodeRunner for node: ${node.id}",
                    context.session
                )
                AgeNodeRunner(context)
            }
            NodeId.SendChat -> {
                WebSocketDebug.logMessage(
                    "SEND_CHAT_RUNNER_SELECTED",
                    "Selected SendChatNodeRunner for node: ${node.id}",
                    context.session
                )
                SendChatNodeRunner(context)
            }
            NodeId.SetStatus -> {
                WebSocketDebug.logMessage(
                    "SET_STATUS_RUNNER_SELECTED",
                    "Selected SetStatusNodeRunner for node: ${node.id}",
                    context.session
                )
                SetStatusNodeRunner(context)
            }
            NodeId.PlayerInput -> {
                WebSocketDebug.logMessage(
                    "PLAYER_INPUT_RUNNER_SELECTED",
                    "Selected PlayerInputNodeRunner for node: ${node.id}",
                    context.session
                )
                PlayerInputNodeRunner(context)
            }
            NodeId.PlayerImageInput -> {
                WebSocketDebug.logMessage(
                    "PLAYER_IMAGE_INPUT_RUNNER_SELECTED",
                    "Selected PlayerImageInputNodeRunner for node: ${node.id}",
                    context.session
                )
                PlayerImageInputNodeRunner(context)
            }
            NodeId.PlayerAudioInput -> {
                WebSocketDebug.logMessage(
                    "PLAYER_AUDIO_INPUT_RUNNER_SELECTED",
                    "Selected PlayerAudioInputNodeRunner for node: ${node.id}",
                    context.session
                )
                PlayerAudioInputNodeRunner(context)
            }
            NodeId.StringTemplate -> {
                WebSocketDebug.logMessage(
                    "STRING_TEMPLATE_RUNNER_SELECTED",
                    "Selected StringTemplateNodeRunner for node: ${node.id}",
                    context.session
                )
                StringTemplateNodeRunner(context)
            }
            NodeId.PlayerName -> {
                WebSocketDebug.logMessage(
                    "PLAYER_NAME_RUNNER_SELECTED",
                    "Selected PlayerNameNodeRunner for node: ${node.id}",
                    context.session
                )
                PlayerNameNodeRunner(context)
            }
            NodeId.Ai -> {
                WebSocketDebug.logMessage(
                    "AI_RUNNER_SELECTED",
                    "Selected AiNodeRunner for node: ${node.id}",
                    context.session
                )
                AiNodeRunner(context)
            }
            NodeId.DocContent -> {
                WebSocketDebug.logMessage(
                    "DOC_CONTENT_RUNNER_SELECTED",
                    "Selected DocContentNodeRunner for node: ${node.id}",
                    context.session
                )
                DocContentNodeRunner(context)
            }
            NodeId.CreateDoc -> {
                WebSocketDebug.logMessage(
                    "CREATE_DOC_RUNNER_SELECTED",
                    "Selected CreateDocNodeRunner for node: ${node.id}",
                    context.session
                )
                CreateDocNodeRunner(context)
            }
            NodeId.IsObjectOwner -> {
                WebSocketDebug.logMessage(
                    "IS_OBJECT_OWNER_RUNNER_SELECTED",
                    "Selected IsObjectOwnerNodeRunner for node: ${node.id}",
                    context.session
                )
                IsObjectOwnerNodeRunner(context)
            }
            NodeId.AiChat -> {
                WebSocketDebug.logMessage(
                    "AI_CHAT_RUNNER_SELECTED",
                    "Selected AiChatNodeRunner for node: ${node.id}",
                    context.session
                )
                AiChatNodeRunner(context)
            }
            NodeId.PlaySound -> {
                WebSocketDebug.logMessage(
                    "PLAY_SOUND_RUNNER_SELECTED",
                    "Selected PlaySoundRunner for node: ${node.id}",
                    context.session
                )
                PlaySoundNodeRunner(context)
            }
            NodeId.JoinList -> {
                WebSocketDebug.logMessage(
                    "JOIN_LIST_RUNNER_SELECTED",
                    "Selected JoinListNodeRunner for node: ${node.id}",
                    context.session
                )
                JoinListNodeRunner(context)
            }
            NodeId.QuestState -> {
                WebSocketDebug.logMessage(
                    "QUEST_STATE_RUNNER_SELECTED",
                    "Selected QuestStateNodeRunner for node: ${node.id}",
                    context.session
                )
                QuestStateNodeRunner(context)
            }
            NodeId.PlayerQuestState -> {
                WebSocketDebug.logMessage(
                    "PLAYER_QUEST_STATE_RUNNER_SELECTED",
                    "Selected PlayerQuestStateNodeRunner for node: ${node.id}",
                    context.session
                )
                PlayerQuestStateNodeRunner(context)
            }
            NodeId.UpdateQuestState -> {
                WebSocketDebug.logMessage(
                    "UPDATE_QUEST_STATE_RUNNER_SELECTED",
                    "Selected UpdateQuestStateNodeRunner for node: ${node.id}",
                    context.session
                )
                UpdateQuestStateNodeRunner(context)
            }
            NodeId.UpdatePlayerQuestState -> {
                WebSocketDebug.logMessage(
                    "UPDATE_PLAYER_QUEST_STATE_RUNNER_SELECTED",
                    "Selected UpdatePlayerQuestStateNodeRunner for node: ${node.id}",
                    context.session
                )
                UpdatePlayerQuestStateNodeRunner(context)
            }
            NodeId.UpdatePlayerQuestStep -> {
                WebSocketDebug.logMessage(
                    "UPDATE_PLAYER_QUEST_STEP_RUNNER_SELECTED",
                    "Selected UpdatePlayerQuestStepNodeRunner for node: ${node.id}",
                    context.session
                )
                UpdatePlayerQuestStepNodeRunner(context)
            }
            NodeId.LocateObject -> {
                WebSocketDebug.logMessage(
                    "LOCATE_OBJECT_RUNNER_SELECTED",
                    "Selected LocateObjectNodeRunner for node: ${node.id}",
                    context.session
                )
                LocateObjectNodeRunner(context)
            }
            NodeId.StartQuest -> {
                WebSocketDebug.logMessage(
                    "START_QUEST_RUNNER_SELECTED",
                    "Selected StartQuestNodeRunner for node: ${node.id}",
                    context.session
                )
                StartQuestNodeRunner(context)
            }
            NodeId.QuestPlayerStatus -> {
                WebSocketDebug.logMessage(
                    "QUEST_PLAYER_STATUS_RUNNER_SELECTED",
                    "Selected QuestPlayerStatusNodeRunner for node: ${node.id}",
                    context.session
                )
                QuestPlayerStatusNodeRunner(context)
            }
            NodeId.QuestStep -> {
                WebSocketDebug.logMessage(
                    "QUEST_STEP_RUNNER_SELECTED",
                    "Selected QuestStepNodeRunner for node: ${node.id}",
                    context.session
                )
                QuestStepNodeRunner(context)
            }
            NodeId.CompareValue -> {
                WebSocketDebug.logMessage(
                    "COMPARE_VALUE_RUNNER_SELECTED",
                    "Selected CompareValueNodeRunner for node: ${node.id}",
                    context.session
                )
                CompareValueNodeRunner(context)
            }
            NodeId.Math -> {
                WebSocketDebug.logMessage(
                    "MATH_RUNNER_SELECTED",
                    "Selected MathNodeRunner for node: ${node.id}",
                    context.session
                )
                MathNodeRunner(context)
            }
            NodeId.AiImage -> {
                WebSocketDebug.logMessage(
                    "AI_IMAGE_RUNNER_SELECTED",
                    "Selected AiImageNodeRunner for node: ${node.id}",
                    context.session
                )
                AiImageNodeRunner(context)
            }
            NodeId.AiTts -> {
                WebSocketDebug.logMessage(
                    "AI_TTS_RUNNER_SELECTED",
                    "Selected AiTtsNodeRunner for node: ${node.id}",
                    context.session
                )
                AiTtsNodeRunner(context)
            }
            NodeId.Minigame -> {
                WebSocketDebug.logMessage(
                    "MINIGAME_RUNNER_SELECTED",
                    "Selected MinigameNodeRunner for node: ${node.id}",
                    context.session
                )
                MinigameNodeRunner(context)
            }
            NodeId.JsonExtract -> {
                WebSocketDebug.logMessage(
                    "JSON_EXTRACT_RUNNER_SELECTED",
                    "Selected JsonExtractNodeRunner for node: ${node.id}",
                    context.session
                )
                JsonExtractNodeRunner(context)
            }
            NodeId.SetAsset -> {
                WebSocketDebug.logMessage(
                    "SET_ASSET_RUNNER_SELECTED",
                    "Selected SetAssetNodeRunner for node: ${node.id}",
                    context.session
                )
                SetAssetNodeRunner(context)
            }
            NodeId.IncreasePlayerSkill -> {
                WebSocketDebug.logMessage(
                    "INCREASE_PLAYER_SKILL_RUNNER_SELECTED",
                    "Selected IncreasePlayerSkillNodeRunner for node: ${node.id}",
                    context.session
                )
                IncreasePlayerSkillNodeRunner(context)
            }
            NodeId.CheckPlayerSkill -> {
                WebSocketDebug.logMessage(
                    "CHECK_PLAYER_SKILL_RUNNER_SELECTED",
                    "Selected CheckPlayerSkillNodeRunner for node: ${node.id}",
                    context.session
                )
                CheckPlayerSkillNodeRunner(context)
            }
            // Add more node runners as needed
            else -> {
                WebSocketDebug.logMessage(
                    "NO_RUNNER_FOUND",
                    "No specific runner found for node category: ${node.nodeCategory}, id: ${node.nodeId}. Using NoOpNodeRunner",
                    context.session
                )
                NoOpNodeRunner()
            }
        }
    }
}
