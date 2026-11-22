@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.evandro.roboolhos.core.robot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.*
import platform.Foundation.NSLocale
import platform.Speech.*

class IosVoiceGateway : VoiceGateway {
    private val mutableState = MutableStateFlow(VoiceState(available = false, detail = "Solicitando permissões…"))
    override val state: StateFlow<VoiceState> = mutableState
    private val mutableEvents = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 4)
    override val events: Flow<VoiceEvent> = mutableEvents
    private val recognizer = SFSpeechRecognizer(NSLocale("pt-BR"))
    private val audioEngine = AVAudioEngine()
    private val synthesizer = AVSpeechSynthesizer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var silenceJob: Job? = null
    private var tapInstalled = false
    private var speechAuthorized: Boolean? = null
    private var microphoneAuthorized: Boolean? = null

    init {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            microphoneAuthorized = granted
            refreshAvailability()
        }
        SFSpeechRecognizer.requestAuthorization { status ->
            speechAuthorized = status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
            refreshAvailability()
        }
    }

    private fun refreshAvailability() {
        val microphone = microphoneAuthorized
        val speech = speechAuthorized
        val available = microphone == true && speech == true
        val detail = when {
            microphone == null || speech == null -> "Solicitando permissões…"
            microphone != true -> "Autorize o microfone nos Ajustes"
            speech != true -> "Autorize o reconhecimento de fala nos Ajustes"
            else -> "Toque para falar"
        }
        mutableState.value = mutableState.value.copy(available = available, detail = detail)
    }

    override suspend fun listen() {
        if (!mutableState.value.available) {
            mutableEvents.tryEmit(VoiceEvent.Error("Permita Microfone e Reconhecimento de Fala nos Ajustes do iPhone."))
            return
        }
        val speechRecognizer = recognizer ?: run {
            mutableEvents.tryEmit(VoiceEvent.Error("Reconhecimento em português indisponível neste iPhone."))
            return
        }
        stopSpeaking(); stopListening()
        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(AVAudioSessionCategoryRecord, null)
        audioSession.setMode(AVAudioSessionModeMeasurement, null)
        audioSession.setActive(true, null)
        val speechRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
            // Let iOS choose local or server recognition. Requiring the local
            // model makes the first use fail silently on devices without pt-BR.
            requiresOnDeviceRecognition = false
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
        silenceJob = scope.launch {
            delay(7_000)
            stopListening()
            mutableEvents.tryEmit(VoiceEvent.Error("Não ouvi nenhuma fala. Tente novamente perto do microfone."))
        }
        task = speechRecognizer.recognitionTaskWithRequest(speechRequest) { result, error ->
            if (result != null) {
                val text = result.bestTranscription.formattedString
                silenceJob?.cancel()
                if (result.final) {
                    finishRecognition(text)
                } else if (text.isNotBlank()) {
                    mutableState.value = mutableState.value.copy(detail = "Entendi: $text")
                    silenceJob = scope.launch {
                        delay(1_200)
                        finishAudioInput()
                    }
                }
            } else if (error != null) {
                stopListening()
                mutableEvents.tryEmit(VoiceEvent.Error(error.localizedDescription))
            }
        }
    }
    private fun finishAudioInput() {
        if (audioEngine.running) audioEngine.stop()
        if (tapInstalled) { audioEngine.inputNode.removeTapOnBus(0u); tapInstalled = false }
        request?.endAudio()
        mutableState.value = mutableState.value.copy(listening = false, detail = "Processando sua fala…")
    }
    private fun finishRecognition(text: String) {
        silenceJob?.cancel()
        finishAudioInput()
        request = null
        task = null
        mutableState.value = mutableState.value.copy(listening = false, detail = "Toque para falar")
        if (text.isBlank()) mutableEvents.tryEmit(VoiceEvent.Error("Não entendi a fala."))
        else mutableEvents.tryEmit(VoiceEvent.Recognized(text))
    }
    override fun stopListening() {
        silenceJob?.cancel(); silenceJob = null
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
    override fun close() { stopListening(); stopSpeaking(); scope.cancel() }
}
