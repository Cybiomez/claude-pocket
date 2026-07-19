package dev.claudepocket.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.claudepocket.AppViewModel

// Кнопка «Проверить обновления» — в шапках экранов подключений и сессий
@Composable
fun UpdateCheckButton(vm: AppViewModel) {
    if (vm.updateChecking) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    } else {
        IconButton(onClick = { vm.checkUpdates() }) {
            Icon(Icons.Filled.SystemUpdate, "Проверить обновления")
        }
    }
}

// Плашка «доступна новая версия» со скачиванием и установкой
@Composable
fun UpdateBanner(vm: AppViewModel) {
    val u = vm.update ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        val p = vm.updateProgress
        val downloaded = vm.downloadedApk != null
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Доступна версия ${u.version}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    when {
                        p != null -> "Скачивание… $p%"
                        downloaded -> "Скачано — нажмите «Установить»"
                        else -> "Нажмите «Обновить», чтобы скачать"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            when {
                // Идёт скачивание — прогресс
                p != null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                // Уже скачано — кнопка запуска установки (можно жать повторно)
                downloaded -> TextButton(onClick = { vm.installDownloaded() }) { Text("Установить") }
                // Ещё не скачано — кнопка скачивания
                else -> TextButton(onClick = { vm.downloadUpdate() }) { Text("Обновить") }
            }
        }
    }
}
