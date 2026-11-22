@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.evandro.roboolhos.core.robot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.*
import platform.Foundation.NSLocale
import platform.Speech.*

class IosVoiceGateway : VoiceGateway {
    private val mutableState = MutableStateFlow(VoiceState())
    override val state: StateFlow<VoiceState> = mutableState
    private val mutableEvents = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 4)
    override val events: Flow<VoiceEvent> = mutableEvents
    private val recognizer = SFSpeechRecognizer(NSLocale("pt-BR"))
    private val audioEngine = AVAudioEngine()
    private val synthesizer = AVSpeechSynthesizer()
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var tapInstalled = false

    init {
        SFSpeechRecognizer.requestAuthorization { status ->
            val available = status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
            mutableState.value = mutableState.value.copy(
                available = available,
                detail = if (available) "Toque para falar" else "Autorize o reconhecimento de voz nos Ajustes",
            )
        }
    }

    override suspend fun listen() {
        if (!mutableState.value.available) {
            mutableEvents.tryEmit(VoiceEvent.Error("Permita Microfone e Reconhecimento de Fala nos Ajustes do iPhone."))
            return
        }
        stopSpeaking(); stopListening()
        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(AVAudioSessionCategoryRecord, null)
        audioSession.setMode(AVAudioSessionModeMeasurement, null)
        audioSession.setActive(true, null)
        val speechRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = false
            requiresOnDeviceRecognition = recognizer?.supportsOnDeviceRecognition == true
        }
        request = speechRequest
        val input = audioEngine.inputNode
        val format = input.outputFormatForBus(0u)
        input.installTapOnBus(0u, 1024u, format) { buffer, _ ->
            buffer?.let(speechRequest::appendAudioPCMBuffer)
        }
        tapInstalled = true
        audioEngine.prepare()
        if (!audioEngine.startAndReturnError(null)) {
            input.removeTapOnBus(0u); tapInstalled = false
            mutableEvents.tryEmit(VoiceEvent.Error("Não foi possível iniciar o microfone."))
            return
        }
        mutableState.value = mutableState.value.copy(listening = true, detail = "Ouvindo… fale agora")
        task = recognizer?.recognitionTaskWithRequest(speechRequest) { result, error ->
            if (result?.final == true) {
                val text = result.bestTranscription.formattedString
                stopListening()
                if (text.isBlank()) mutableEvents.tryEmit(VoiceEvent.Error("Não entendi a fala."))
                else mutableEvents.tryEmit(VoiceEvent.Recognized(text))
            } else if (error != null) {
                stopListening()
                mutableEvents.tryEmit(VoiceEvent.Error(error.localizedDescription))
            }
        }
    }
    override fun stopListening() {
        if (audioEngine.running) audioEngine.stop()
        if (tapInstalled) { audioEngine.inputNode.removeTapOnBus(0u); tapInstalled = false }
        request?.endAudio(); task?.cancel(); request = null; task = null
        mutableState.value = mutableState.value.copy(listening = false, detail = "Toque para falar")
    }
    override fun speak(text: String) {
        if (text.isBlank()) return
        stopListening(); synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text).apply {
            voice = AVSpeechSynthesisVoice.voiceWithLanguage("pt-BR")
            rate = AVSpeechUtteranceDefaultSpeechRate * .92f
        }
        mutableState.value = mutableState.value.copy(speaking = true, detail = "Robô falando no iPhone")
        synthesizer.speakUtterance(utterance)
    }
    override fun stopSpeaking() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        mutableState.value = mutableState.value.copy(speaking = false, detail = "Toque para falar")
    }
    override fun close() { stopListening(); stopSpeaking() }
}
