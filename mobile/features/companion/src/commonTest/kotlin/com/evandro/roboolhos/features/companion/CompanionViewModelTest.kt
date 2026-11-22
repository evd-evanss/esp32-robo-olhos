package com.evandro.roboolhos.features.companion

import com.evandro.roboolhos.core.robot.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var robot: FakeRobot
    private lateinit var voice: FakeVoice
    private lateinit var viewModel: CompanionViewModel
    @BeforeTest fun before() { Dispatchers.setMain(dispatcher); robot = FakeRobot(); voice = FakeVoice(); viewModel = CompanionViewModel(robot, voice); dispatcher.scheduler.runCurrent() }
    @AfterTest fun after() { Dispatchers.resetMain() }

    @Test fun recognizedSpeechIsSentAndReplyIsSpoken() = runTest(dispatcher) {
        voice.eventsFlow.emit(VoiceEvent.Recognized("estou feliz")); advanceUntilIdle()
        assertEquals(listOf("estou feliz"), robot.sent)
        assertEquals("Que bom!", voice.spoken.single())
        assertFalse(viewModel.state.value.busy)
    }
    @Test fun disabledSoundDoesNotSpeak() = runTest(dispatcher) {
        viewModel.dispatch(CompanionIntent.ToggleSound)
        viewModel.dispatch(CompanionIntent.DraftChanged("olá")); viewModel.dispatch(CompanionIntent.Send); advanceUntilIdle()
        assertEquals(emptyList(), voice.spoken)
        assertEquals("olá", robot.sent.single())
    }
}

private class FakeRobot : RobotGateway {
    override val connection = MutableStateFlow(LinkState(LinkPhase.READY, "pronto", "teste"))
    override val devices = MutableStateFlow(emptyList<RobotDevice>())
    val sent = mutableListOf<String>()
    override suspend fun scan() = Unit
    override fun stopScan() = Unit
    override suspend fun connect(device: RobotDevice) = Unit
    override fun disconnect() = Unit
    override suspend fun send(text: String): RobotReply { sent += text; return RobotReply(sent.size.toLong(), "Que bom!", RobotState(face = "feliz")) }
}
private class FakeVoice : VoiceGateway {
    override val state = MutableStateFlow(VoiceState())
    val eventsFlow = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 2)
    override val events: Flow<VoiceEvent> = eventsFlow
    val spoken = mutableListOf<String>()
    override suspend fun listen() = Unit
    override fun stopListening() = Unit
    override fun speak(text: String) { spoken += text }
    override fun stopSpeaking() = Unit
    override fun close() = Unit
}
