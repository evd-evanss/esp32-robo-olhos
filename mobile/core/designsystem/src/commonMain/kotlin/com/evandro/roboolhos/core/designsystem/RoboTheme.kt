package com.evandro.roboolhos.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val Mint = Color(0xFF78F0C0)
val Navy = Color(0xFF101820)
val Panel = Color(0xFF1A2632)

@Composable
fun RoboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Color(0xFF06271C),
            primaryContainer = Color(0xFF164E3D),
            onPrimaryContainer = Color(0xFFA6F8D7),
            secondary = Color(0xFFA9C0CE),
            secondaryContainer = Color(0xFF243947),
            tertiary = Color(0xFFB8ACFF),
            background = Navy,
            onBackground = Color(0xFFF0F6F9),
            surface = Panel,
            onSurface = Color(0xFFF0F6F9),
            surfaceVariant = Color(0xFF223441),
            onSurfaceVariant = Color(0xFFBBC9D1),
            outline = Color(0xFF526773),
            error = Color(0xFFFFB4AB),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}
