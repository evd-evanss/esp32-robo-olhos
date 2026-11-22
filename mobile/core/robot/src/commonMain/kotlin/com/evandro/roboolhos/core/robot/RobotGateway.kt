package com.evandro.roboolhos.core.robot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class RobotState(
    val face: String = "neutro", val mood: String = "neutro", val interaction: String = "normal", val name: String = "",
    val interest: String = "", val pace: String = "calmo", val demo: Boolean = false,
    val memory: Boolean = true, val lessons: Int = 0, val dialogues: Int = 0,
)
@Serializable
data class RobotReply(val id: Long, val reply: String = "", val state: RobotState = RobotState(), val error: String? = null)
data class RobotDevice(val id: String, val name: String, val signal: Int = 0)
enum class LinkPhase { DISCONNECTED, SCANNING, CONNECTING, READY }
data class LinkState(val phase: LinkPhase = LinkPhase.DISCONNECTED, val detail: String = "Conecte seu robô", val deviceName: String = "")
interface RobotGateway {
    val connection: StateFlow<LinkState>
    val devices: StateFlow<List<RobotDevice>>
    suspend fun scan()
    fun stopScan()
    suspend fun connect(device: RobotDevice)
    fun disconnect()
    suspend fun send(text: String): RobotReply
    suspend fun signal(interaction: String) { send("/estado $interaction") }
}

data class VoiceState(val listening: Boolean = false, val speaking: Boolean = false, val detail: String = "Toque para falar", val available: Boolean = true)
sealed interface VoiceEvent {
    data class Recognized(val text: String) : VoiceEvent
    data class Error(val message: String) : VoiceEvent
}
interface VoiceGateway {
    val state: StateFlow<VoiceState>
    val events: Flow<VoiceEvent>
    suspend fun listen()
    fun stopListening()
    fun speak(text: String)
    fun stopSpeaking()
    fun close()
}
