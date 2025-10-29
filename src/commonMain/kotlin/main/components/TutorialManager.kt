package main.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Data class representing a tutorial step with all its information.
 *
 * @property id Unique identifier for the step
 * @property text Text to display in the tutorial bubble
 * @property targetButtonId ID of the button this step is targeting
 * @property order Order of this step in the tutorial sequence (lower numbers come first)
 */
data class TutorialStepInfo(
    val id: String,
    val text: String,
    val targetButtonId: String,
    val order: Int
)

/**
 * Sealed class representing the different tutorial steps.
 * Each step has a corresponding TutorialStepInfo.
 */
sealed class TutorialStep {
    /**
     * First tutorial step: Create your player by clicking the profile button.
     */
    object CreatePlayer : TutorialStep()
    
    /**
     * Second tutorial step: Open world chat by clicking the chat button.
     */
    object OpenChat : TutorialStep()
    
    /**
     * Third tutorial step: Share this world by clicking the QR code button.
     */
    object ShareWorld : TutorialStep()
    
    /**
     * Tutorial completed state.
     */
    object Completed : TutorialStep()
    
    companion object {
        /**
         * Get the TutorialStepInfo for a given TutorialStep.
         */
        fun getStepInfo(step: TutorialStep): TutorialStepInfo? {
            return when (step) {
                is CreatePlayer -> TutorialStepInfo(
                    id = "create_player",
                    text = "Create your player",
                    targetButtonId = "profileButton",
                    order = 1
                )
                is OpenChat -> TutorialStepInfo(
                    id = "open_chat",
                    text = "Open chat",
                    targetButtonId = "chatButton",
                    order = 2
                )
                is ShareWorld -> TutorialStepInfo(
                    id = "share_world",
                    text = "Share this world",
                    targetButtonId = "qrButton",
                    order = 3
                )
                is Completed -> null
            }
        }
        
        /**
         * Get the next step after the given step.
         */
        fun getNextStep(currentStep: TutorialStep): TutorialStep {
            return when (currentStep) {
                is CreatePlayer -> OpenChat
                is OpenChat -> ShareWorld
                is ShareWorld -> Completed
                is Completed -> Completed // Already completed
            }
        }
        
        /**
         * Get all tutorial steps in order.
         */
        fun getAllSteps(): List<TutorialStep> {
            return listOf(CreatePlayer, OpenChat, ShareWorld)
        }
    }
}

/**
 * Manager for the tutorial system.
 * Tracks the current tutorial step and provides methods to advance through the tutorial.
 */
class TutorialManager {
    // Current step in the tutorial
    var currentStep by mutableStateOf<TutorialStep>(TutorialStep.CreatePlayer)
        private set
    
    // Whether the tutorial is visible
    var isTutorialVisible by mutableStateOf(true)
        private set
    
    /**
     * Advances to the next tutorial step.
     * If the current step is the last one, marks the tutorial as completed.
     */
    fun advanceToNextStep() {
        currentStep = TutorialStep.getNextStep(currentStep)
    }
    
    /**
     * Hides the tutorial.
     */
    fun hideTutorial() {
        isTutorialVisible = false
    }
    
    /**
     * Shows the tutorial.
     */
    fun showTutorial() {
        isTutorialVisible = true
    }
    
    /**
     * Resets the tutorial to the first step.
     */
    fun resetTutorial() {
        currentStep = TutorialStep.CreatePlayer
        isTutorialVisible = true
    }
    
    /**
     * Returns the text for the current tutorial step.
     */
    fun getCurrentStepText(): String {
        return TutorialStep.getStepInfo(currentStep)?.text ?: ""
    }
    
    /**
     * Returns the target button ID for the current tutorial step.
     */
    fun getCurrentStepTargetButtonId(): String? {
        return TutorialStep.getStepInfo(currentStep)?.targetButtonId
    }
    
    /**
     * Returns whether the tutorial is completed.
     */
    fun isTutorialCompleted(): Boolean {
        return currentStep is TutorialStep.Completed
    }
    
    /**
     * Returns the current step info.
     */
    fun getCurrentStepInfo(): TutorialStepInfo? {
        return TutorialStep.getStepInfo(currentStep)
    }
    
    /**
     * Checks if the given button is the target of the current tutorial step.
     */
    fun isButtonCurrentTarget(buttonId: String): Boolean {
        return getCurrentStepTargetButtonId() == buttonId
    }
}
