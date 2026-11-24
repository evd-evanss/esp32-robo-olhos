package com.evandro.roboolhos

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.evandro.roboolhos.core.robot.AndroidRobotGateway
import com.evandro.roboolhos.core.robot.AndroidVoiceGateway
import com.evandro.roboolhos.core.robot.ConversationEngine
import com.evandro.roboolhos.core.robot.EmbeddedFallbackEngine
import com.evandro.roboolhos.core.robot.RobotGateway
import com.evandro.roboolhos.core.robot.VoiceGateway
import com.evandro.roboolhos.features.companion.CompanionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    private lateinit var voice: AndroidVoiceGateway
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = AndroidVoiceGateway(applicationContext)
        val robot = AndroidRobotGateway(applicationContext)
        runCatching {
            startKoin {
                androidContext(applicationContext)
                modules(module {
                    single<RobotGateway> { robot }
                    single<VoiceGateway> { voice }
                    single<ConversationEngine> { EmbeddedFallbackEngine }
                    factory { CompanionViewModel(get(), get(), get()) }
                })
            }
        }
        permissions.launch(buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
            else add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.toTypedArray())
        setContent { App() }
    }
    override fun onStop() { voice.stopListening(); voice.stopSpeaking(); super.onStop() }
}
