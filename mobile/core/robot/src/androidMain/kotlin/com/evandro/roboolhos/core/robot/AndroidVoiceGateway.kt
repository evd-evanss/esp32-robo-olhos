package com.evandro.roboolhos.core.robot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class AndroidVoiceGateway(context: Context) : VoiceGateway, RecognitionListener, TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(VoiceState(available = SpeechRecognizer.isRecognitionAvailable(appContext)))
    override val state: StateFlow<VoiceState> = mutableState
    private val mutableEvents = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 4)
    override val events: Flow<VoiceEvent> = mutableEvents
    private val recognizer = if (mutableState.value.available) SpeechRecognizer.createSpeechRecognizer(appContext).also { it.setRecognitionListener(this) } else null
    private val tts = TextToSpeech(appContext, this)
    private var ttsReady = false

    override suspend fun listen() {
        val speech = recognizer ?: return mutableEvents.tryEmit(VoiceEvent.Error("Reconhecimento de voz não disponível neste celular.")).let { Unit }
        stopSpeaking()
        mutableState.value = mutableState.value.copy(listening = true, detail = "Ouvindo… fale agora")
        speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        })
    }
    override fun stopListening() { recognizer?.cancel(); mutableState.value = mutableState.value.copy(listening = false, detail = "Toque para falar") }
    override fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        mutableState.value = mutableState.value.copy(speaking = true, detail = "Robô falando no celular")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "robo-reply")
    }
    override fun stopSpeaking() { tts.stop(); mutableState.value = mutableState.value.copy(speaking = false, detail = "Toque para falar") }
    override fun close() { recognizer?.destroy(); tts.stop(); tts.shutdown() }
    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS; if (ttsReady) { tts.language = Locale.forLanguageTag("pt-BR"); tts.setSpeechRate(.95f) } }
    override fun onReadyForSpeech(params: Bundle?) { mutableState.value = mutableState.value.copy(detail = "Pode falar") }
    override fun onBeginningOfSpeech() { mutableState.value = mutableState.value.copy(detail = "Estou ouvindo") }
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() { mutableState.value = mutableState.value.copy(listening = false, detail = "Entendendo sua fala…") }
    override fun onError(error: Int) {
        mutableState.value = mutableState.value.copy(listening = false, detail = "Toque para tentar novamente")
        mutableEvents.tryEmit(VoiceEvent.Error(when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi. Tente falar mais perto do celular."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não ouvi nenhuma fala."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permita o uso do microfone nas configurações."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "O reconhecimento de voz deste celular precisa de internet."
            else -> "Não foi possível reconhecer a fala ($error)."
        }))
    }
    override fun onResults(results: Bundle?) {
        mutableState.value = mutableState.value.copy(listening = false, detail = "Toque para falar")
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isBlank()) mutableEvents.tryEmit(VoiceEvent.Error("Não entendi a fala.")) else mutableEvents.tryEmit(VoiceEvent.Recognized(text))
    }
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
