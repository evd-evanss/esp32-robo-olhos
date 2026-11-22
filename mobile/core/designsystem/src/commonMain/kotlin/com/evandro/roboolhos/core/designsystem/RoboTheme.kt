package com.evandro.roboolhos.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFF78F0C0)
val Navy = Color(0xFF101820)
val Panel = Color(0xFF1A2632)

@Composable
fun RoboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint, onPrimary = Color(0xFF09251D), background = Navy,
            onBackground = Color(0xFFEEF6FA), surface = Panel, onSurface = Color(0xFFEEF6FA),
            secondary = Color(0xFFA7BECb), error = Color(0xFFFFB4AB),
        ),
        content = content,
    )
}
