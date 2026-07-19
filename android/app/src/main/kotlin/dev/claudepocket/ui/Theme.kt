package dev.claudepocket.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Единый источник цветов приложения. Каноничные оттенки Claude:
// терракотовый акцент, тёплые бежево-серые тона. Светлая тема — зеркало тёмной:
// «белым» служит бежевый цвет букв, текстом — тёмно-серый (не чёрный).
object Palette {
    // Акцент — общий для обеих тем
    val Terracotta = Color(0xFFD97757)
    val OnAccent = Color(0xFFFFFFFF)

    // Тёмная тема
    val DarkBackground = Color(0xFF1F1E1D)
    val DarkSurface = Color(0xFF262524)
    val DarkSurfaceVariant = Color(0xFF1A1918)   // темнее surface — пузыри ответов «чуть темнее»
    val DarkText = Color(0xFFEDEAE6)             // тёплый бежевый
    val DarkSecondary = Color(0xFFB8A99A)

    // Светлая тема (зеркало): фон — бежевый цвет букв, текст — тёмно-серый
    val LightBackground = Color(0xFFF2EFE8)      // бежевый, не чисто белый
    val LightSurface = Color(0xFFFAF8F3)         // чуть светлее фона — карточки/панели
    val LightSurfaceVariant = Color(0xFFE7E2D8)  // сероватый, темнее surface — пузыри ответов
    val LightText = Color(0xFF262524)            // тёмно-серый, не чёрный
    val LightSecondary = Color(0xFF6B6156)
}

private val DarkScheme = darkColorScheme(
    primary = Palette.Terracotta,
    onPrimary = Palette.OnAccent,
    secondary = Palette.DarkSecondary,
    background = Palette.DarkBackground,
    surface = Palette.DarkSurface,
    surfaceVariant = Palette.DarkSurfaceVariant,
    onBackground = Palette.DarkText,
    onSurface = Palette.DarkText,
)

private val LightScheme = lightColorScheme(
    primary = Palette.Terracotta,
    onPrimary = Palette.OnAccent,
    secondary = Palette.LightSecondary,
    background = Palette.LightBackground,
    surface = Palette.LightSurface,
    surfaceVariant = Palette.LightSurfaceVariant,
    onBackground = Palette.LightText,
    onSurface = Palette.LightText,
)

// Режим темы: следовать системе / всегда тёмная / всегда светлая
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}
