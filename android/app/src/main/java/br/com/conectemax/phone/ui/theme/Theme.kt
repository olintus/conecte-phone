package br.com.conectemax.phone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF071B36)
val NavySoft = Color(0xFF102945)
val Lime = Color(0xFFB7F23A)
val Ice = Color(0xFFF4F7F9)
val Blue = Color(0xFF1F70FF)
val Danger = Color(0xFFE84B55)
val Muted = Color(0xFF718096)

private val Light = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Lime,
    onSecondary = Navy,
    background = Ice,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Navy,
    error = Danger,
)

private val Dark = darkColorScheme(
    primary = Lime,
    onPrimary = Navy,
    secondary = Lime,
    background = Color(0xFF051426),
    surface = NavySoft,
    onSurface = Color.White,
)

@Composable
fun ConecteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}

