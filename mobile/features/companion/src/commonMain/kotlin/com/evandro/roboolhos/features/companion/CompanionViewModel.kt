package com.evandro.roboolhos.features.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evandro.roboolhos.core.robot.LinkPhase
import com.evandro.roboolhos.core.robot.RobotGateway
import com.evandro.roboolhos.core.robot.VoiceEvent
import com.evandro.roboolhos.core.robot.VoiceGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CompanionViewModel(private val robotGateway: RobotGateway, private val voiceGateway: VoiceGateway) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanionState())
    val state: StateFlow<CompanionState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { robotGateway.connection.collect { link -> mutableState.update { it.copy(link = link) } } }
        viewModelScope.launch { robotGateway.devices.collect { devices -> mutableState.update { it.copy(devices = devices) } } }
        viewModelScope.launch { voiceGateway.state.collect { voice -> mutableState.update { it.copy(voice = voice) } } }
        viewModelScope.launch {
            voiceGateway.events.collect { event ->
                when (event) {
                    is VoiceEvent.Recognized -> { mutableState.update { it.copy(draft = event.text) }; send(event.text) }
                    is VoiceEvent.Error -> mutableState.update { it.copy(error = event.message) }
                }
            }
        }
    }

    fun dispatch(intent: CompanionIntent) {
        when (intent) {
            CompanionIntent.Scan -> viewModelScope.launch { runAction { robotGateway.scan() } }
            is CompanionIntent.Connect -> viewModelScope.launch { runAction { robotGateway.connect(intent.device) } }
            CompanionIntent.Disconnect -> robotGateway.disconnect()
            is CompanionIntent.DraftChanged -> mutableState.update { it.copy(draft = intent.value.take(240)) }
            CompanionIntent.Send -> send(state.value.draft)
            CompanionIntent.Listen -> viewModelScope.launch {
                voiceGateway.stopSpeaking()
                runAction { voiceGateway.listen() }
            }
            CompanionIntent.ToggleSound -> mutableState.update { it.copy(soundEnabled = !it.soundEnabled) }
            is CompanionIntent.Command -> send(intent.value)
            is CompanionIntent.Correct -> send("/corrigir ${intent.emotion}")
            is CompanionIntent.TrainingPromptChanged -> mutableState.update { it.copy(trainingPrompt = intent.value.take(80).replace("|", "")) }
            is CompanionIntent.TrainingReplyChanged -> mutableState.update { it.copy(trainingReply = intent.value.take(120).replace("|", "")) }
            is CompanionIntent.TrainingFaceChanged -> mutableState.update { it.copy(trainingFace = intent.value) }
            CompanionIntent.TrainConversation -> trainConversation()
            CompanionIntent.DismissError -> mutableState.update { it.copy(error = null) }
        }
    }

    private fun trainConversation() {
        val snapshot = state.value
        val prompt = snapshot.trainingPrompt.trim()
        val reply = snapshot.trainingReply.trim()
        if (prompt.isEmpty() || reply.isEmpty() || snapshot.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            try {
                val result = robotGateway.send("/ensinar|${snapshot.trainingFace}|$prompt|$reply")
                if (result.error != null) throw IllegalStateException(result.error)
                mutableState.update {
                    it.copy(
                        robot = result.state,
                        trainingPrompt = "",
                        trainingReply = "",
                        messages = (it.messages + ChatMessage(result.reply, true)).takeLast(40),
                    )
                }
                if (result.reply.isNotBlank() && state.value.soundEnabled) voiceGateway.speak(result.reply)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { mutableState.update { it.copy(error = error.message ?: "Não foi possível ensinar.") } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    private fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || state.value.busy) return
        if (state.value.link.phase != LinkPhase.READY) { mutableState.update { it.copy(error = "Conecte o Robô Olhos primeiro.") }; return }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, draft = "", lastUserText = if (text.startsWith('/')) it.lastUserText else text, messages = (it.messages + ChatMessage(text, false)).takeLast(40)) }
            try {
                val reply = robotGateway.send(text)
                if (reply.error != null) throw IllegalStateException(reply.error)
                mutableState.update { it.copy(robot = reply.state, messages = if (reply.reply.isBlank()) it.messages else (it.messages + ChatMessage(reply.reply, true)).takeLast(40)) }
                if (reply.reply.isNotBlank() && state.value.soundEnabled) voiceGateway.speak(reply.reply.substringBefore(" ["))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { mutableState.update { it.copy(error = error.message ?: "Falha na comunicação Bluetooth.") } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    private suspend fun runAction(block: suspend () -> Unit) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { mutableState.update { it.copy(error = error.message ?: "Não foi possível concluir.") } }
    }

    override fun onCleared() { robotGateway.disconnect(); voiceGateway.close(); super.onCleared() }
}
