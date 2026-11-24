package com.evandro.roboolhos

import androidx.compose.ui.window.ComposeUIViewController
import com.evandro.roboolhos.core.robot.IosRobotGateway
import com.evandro.roboolhos.core.robot.IosVoiceGateway
import com.evandro.roboolhos.core.robot.CallbackConversationEngine
import com.evandro.roboolhos.core.robot.ConversationEngine
import com.evandro.roboolhos.core.robot.RobotGateway
import com.evandro.roboolhos.core.robot.VoiceGateway
import com.evandro.roboolhos.features.companion.CompanionViewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

private lateinit var conversationEngine: ConversationEngine
private val iosKoin by lazy {
    startKoin {
        modules(module {
            single<RobotGateway> { IosRobotGateway() }
            single<VoiceGateway> { IosVoiceGateway() }
            single<ConversationEngine> { conversationEngine }
            factory { CompanionViewModel(get(), get(), get()) }
        })
    }
}

fun MainViewController(
    aiAvailable: Boolean,
    aiResponder: (String, (String?, String?) -> Unit) -> Unit,
) = run {
    conversationEngine = CallbackConversationEngine(aiAvailable, aiResponder)
    iosKoin
    ComposeUIViewController { App() }
}
