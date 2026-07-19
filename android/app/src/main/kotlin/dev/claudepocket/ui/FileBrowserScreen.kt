package dev.claudepocket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.claudepocket.AppViewModel
import dev.claudepocket.net.FileEntry

// Read-only просмотр файлов сервера: каталоги, текст (с markdown-рендером), картинки
@Composable
fun FileBrowserScreen(vm: AppViewModel) {
    BackHandler { vm.closeFileBrowser() }
    val entry = vm.fileEntry

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navigateUp(vm, entry) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
            // Рядом с «Назад»: сразу в список сессий из любой глубины каталогов
            IconButton(onClick = { vm.closeFileBrowser() }) {
                Icon(Icons.AutoMirrored.Filled.List, "К списку сессий", Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Файлы", style = MaterialTheme.typography.titleMedium)
                if (entry != null) Text(
                    shortenPath(entry.path), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                vm.fileLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                vm.fileError != null -> Text(
                    "Ошибка: ${vm.fileError}", color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                entry == null -> {}
                entry.isDir -> DirList(vm, entry)
                else -> FileContent(entry)
            }
        }
    }
}

// Вверх: из корня доступной области (домашней папки) — обратно в список сессий;
// иначе из файла — к его каталогу, из каталога — к родителю.
private fun navigateUp(vm: AppViewModel, entry: FileEntry?) {
    if (entry == null) { vm.closeFileBrowser(); return }
    // В корне (или выше него подниматься нельзя) — закрываем браузер, не дёргая демон
    if (entry.path == vm.fileHomeRoot) { vm.closeFileBrowser(); return }
    val parent = entry.path.substringBeforeLast('/', "")
    if (parent.isBlank()) vm.closeFileBrowser() else vm.loadFile(parent)
}

@Composable
private fun DirList(vm: AppViewModel, entry: FileEntry) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(entry.entries) { child ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { vm.loadFile(entry.path + "/" + child.name) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (child.dir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    null, Modifier.size(20.dp),
                    tint = if (child.dir) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.size(12.dp))
                Text(child.name, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FileContent(entry: FileEntry) {
    if (entry.encoding == "base64") {
        // Картинка — рисуем, прочее бинарное — просто размер
        val bytes = runCatching { android.util.Base64.decode(entry.content, android.util.Base64.DEFAULT) }.getOrNull()
        val bmp = bytes?.let {
            runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }
        if (bmp != null) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Image(bmp.asImageBitmap(), entry.path, Modifier.fillMaxWidth())
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Бинарный файл, ${entry.size} байт", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }
        return
    }

    val isMarkdown = entry.path.endsWith(".md", true) || entry.path.endsWith(".markdown", true)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    ) {
        if (isMarkdown) {
            MarkdownText(entry.content)
        } else {
            // Код и прочий текст — моноширинно, с горизонтальной прокруткой длинных строк
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Text(entry.content, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

private fun shortenPath(p: String, keep: Int = 40): String =
    if (p.length <= keep) p else "…" + p.takeLast(keep)
