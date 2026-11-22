package com.evandro.roboolhos

import androidx.compose.runtime.Composable
import com.evandro.roboolhos.core.designsystem.RoboTheme
import com.evandro.roboolhos.features.companion.CompanionScreen
import com.evandro.roboolhos.features.companion.CompanionViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() = RoboTheme { CompanionScreen(koinViewModel<CompanionViewModel>()) }
