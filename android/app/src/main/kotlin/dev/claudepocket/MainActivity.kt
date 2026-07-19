package dev.claudepocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.claudepocket.ui.AppRoot

private val Terracotta = Color(0xFFD97757)
private val DarkScheme = darkColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    secondary = Color(0xFFB8A99A),
    background = Color(0xFF1F1E1D),
    surface = Color(0xFF262524),
    // Темнее surface — на нём пузыри ответов ассистента читаются как «чуть темнее»
    surfaceVariant = Color(0xFF1A1918),
    onBackground = Color(0xFFEDEAE6),
    onSurface = Color(0xFFEDEAE6),
)
private val LightScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    background = Color(0xFFFAF9F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0EEE9),
    onBackground = Color(0xFF262524),
    onSurface = Color(0xFF262524),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot(viewModel()) } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}
