package com.evandro.roboolhos

import androidx.compose.ui.window.ComposeUIViewController
import com.evandro.roboolhos.core.robot.IosRobotGateway
import com.evandro.roboolhos.core.robot.IosVoiceGateway
import com.evandro.roboolhos.core.robot.RobotGateway
import com.evandro.roboolhos.core.robot.VoiceGateway
import com.evandro.roboolhos.features.companion.CompanionViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun MainViewController() = ComposeUIViewController {
    if (GlobalContext.getOrNull() == null) {
        startKoin {
            modules(module {
                single<RobotGateway> { IosRobotGateway() }
                single<VoiceGateway> { IosVoiceGateway() }
                factory { CompanionViewModel(get(), get()) }
            })
        }
    }
    App()
}
