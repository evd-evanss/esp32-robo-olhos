package com.evandro.roboolhos.features.companion

import com.evandro.roboolhos.core.robot.LinkState
import com.evandro.roboolhos.core.robot.RobotDevice
import com.evandro.roboolhos.core.robot.RobotState
import com.evandro.roboolhos.core.robot.VoiceState

data class ChatMessage(val text: String, val fromRobot: Boolean)
data class CompanionState(
    val link: LinkState = LinkState(), val devices: List<RobotDevice> = emptyList(),
    val robot: RobotState = RobotState(), val voice: VoiceState = VoiceState(),
    val messages: List<ChatMessage> = listOf(ChatMessage("Olá! Conecte o Robô Olhos e toque no microfone.", true)),
    val draft: String = "", val busy: Boolean = false, val soundEnabled: Boolean = true,
    val error: String? = null, val lastUserText: String = "",
    val trainingPrompt: String = "", val trainingReply: String = "", val trainingFace: String = "feliz",
)
sealed interface CompanionIntent {
    data object Scan : CompanionIntent
    data class Connect(val device: RobotDevice) : CompanionIntent
    data object Disconnect : CompanionIntent
    data class DraftChanged(val value: String) : CompanionIntent
    data object Send : CompanionIntent
    data object Listen : CompanionIntent
    data object ToggleSound : CompanionIntent
    data class Command(val value: String) : CompanionIntent
    data class Correct(val emotion: String) : CompanionIntent
    data class TrainingPromptChanged(val value: String) : CompanionIntent
    data class TrainingReplyChanged(val value: String) : CompanionIntent
    data class TrainingFaceChanged(val value: String) : CompanionIntent
    data object TrainConversation : CompanionIntent
    data object DismissError : CompanionIntent
}
