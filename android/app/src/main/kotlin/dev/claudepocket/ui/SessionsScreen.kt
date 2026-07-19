package dev.claudepocket.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.claudepocket.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsScreen(vm: AppViewModel) {
    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.newTab() }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, "Новая сессия", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.padding(horizontal = 16.dp)) { UpdateBanner(vm) }
            // Заголовок отдельной строкой — не «едет» от числа кнопок ниже
            Text(
                "Сессии", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Выход к экрану выбора сервера (разрыв соединения). Иконка отзеркалена —
                // стрелка «двери» смотрит влево, к экрану выбора.
                IconButton(onClick = { vm.disconnect() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout, "К выбору сервера",
                        Modifier.size(20.dp).graphicsLayer(scaleX = -1f),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Смена темы: система / светлая / тёмная
                IconButton(onClick = { vm.cycleTheme() }) {
                    Icon(
                        when (vm.themeMode) {
                            dev.claudepocket.ui.ThemeMode.LIGHT -> Icons.Filled.LightMode
                            dev.claudepocket.ui.ThemeMode.DARK -> Icons.Filled.DarkMode
                            else -> Icons.Filled.BrightnessAuto
                        },
                        "Тема оформления", Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { vm.openFileBrowser() }) {
                    Icon(Icons.Filled.FolderOpen, "Файлы сервера", Modifier.size(20.dp))
                }
                UpdateCheckButton(vm)
                IconButton(onClick = { vm.refreshSessions(); vm.refreshUsage() }) {
                    Icon(Icons.Filled.Refresh, "Обновить", Modifier.size(20.dp))
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(vm.sessions, key = { it.id }) { s ->
                    Card(
                        onClick = { vm.openTab(s.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),   // скруглены как пузыри чата
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (s.running) Box(
                                    Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape)
                                )
                                if (s.running) Spacer(Modifier.size(6.dp))
                                Text(
                                    s.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatTime(s.mtime), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                )
                            }
                            if (s.lastText.isNotBlank()) {
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    s.lastText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }
            SessionsFooter(vm)
        }
    }
}

// Нижний футер, как в чате: слева индикатор загрузки, лимиты — по центру
// (между двумя распорками) свободного места; без загрузки — по центру строки.
@Composable
private fun SessionsFooter(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.sessionsLoading) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Обновляю…", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.weight(1f))
        UsageLine(vm)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun UsageLine(vm: AppViewModel) {
    val u = vm.usage ?: return
    if (!u.available) return
    Text(
        "5 ч: ${u.fiveHourPct ?: "—"}% · неделя: ${u.sevenDayPct ?: "—"}%",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
    )
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000 -> "сейчас"
        diff < 3_600_000 -> "${diff / 60_000} мин"
        diff < 86_400_000 -> "${diff / 3_600_000} ч"
        else -> SimpleDateFormat("d MMM", Locale("ru")).format(Date(ms))
    }
}
