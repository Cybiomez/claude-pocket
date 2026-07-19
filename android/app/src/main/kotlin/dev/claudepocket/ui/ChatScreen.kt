package dev.claudepocket.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.claudepocket.AppViewModel
import dev.claudepocket.ChatItem
import dev.claudepocket.ChatState
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: AppViewModel) {
    val tab = vm.activeTab ?: return
    val chat = vm.chats[tab] ?: return

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        TabsBar(vm)
        Box(Modifier.weight(1f)) {
            when {
                chat.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> MessageList(chat)
            }
        }
        StatusFooter(vm, chat)
        InputBar(vm, tab, chat)
    }
}

@Composable
private fun TabsBar(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = { vm.activeTab = null }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "К списку", modifier = Modifier.size(20.dp))
        }
        for (t in vm.tabs) {
            val active = t == vm.activeTab
            val title = vm.chats[t]?.title?.ifBlank { null }
                ?: vm.sessions.firstOrNull { it.id == t }?.title
                ?: if (t.startsWith("new-")) "Новая" else t.take(8)
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.activeTab = t }
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.chats[t]?.running == true) {
                    CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(title.take(18), fontSize = 12.sp, maxLines = 1)
                IconButton(onClick = { vm.closeTab(t) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, "Закрыть", modifier = Modifier.size(13.dp))
                }
            }
        }
        IconButton(onClick = { vm.newTab() }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Add, "Новая сессия", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MessageList(chat: ChatState) {
    val listState = rememberLazyListState()
    val total = chat.items.size + (if (chat.streaming.isNotBlank()) 1 else 0)
    LaunchedEffect(total, chat.streaming.length / 200) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chat.items, key = { it.itemKey }) { item -> ChatItemView(item) }
        if (chat.streaming.isNotBlank()) {
            item(key = "streaming") { AssistantBubble { MarkdownText(chat.streaming) } }
        } else if (chat.running) {
            item(key = "typing") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Работаю…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private val ChatItem.itemKey: String
    get() = when (this) {
        is ChatItem.Text -> key
        is ChatItem.Thinking -> key
        is ChatItem.Tool -> key
        is ChatItem.SystemNote -> key
    }

@Composable
private fun ChatItemView(item: ChatItem) {
    when (item) {
        is ChatItem.Text ->
            if (item.role == "user") UserBubble(item.text) else AssistantBubble { MarkdownText(item.text) }
        is ChatItem.Thinking -> CollapsibleRow(
            title = "Размышления", subtitle = item.text.take(60).replace('\n', ' '),
        ) {
            Text(item.text, fontSize = 12.sp, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
        is ChatItem.Tool -> CollapsibleRow(
            title = toolLabel(item.name, item.input),
            subtitle = if (item.result == null) "выполняется…" else (if (item.isError) "ошибка" else "готово"),
            error = item.isError,
            inProgress = item.result == null,
        ) {
            Column {
                Text("Ввод:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(item.input.take(1500), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                if (item.result != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Результат:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(item.result.take(3000), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
        is ChatItem.SystemNote -> Text(
            item.text, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

// «читаю main.py», «запускаю npm test» — человеческие подписи действий
private fun toolLabel(name: String, inputJson: String): String {
    fun field(k: String): String? =
        Regex("\"$k\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(inputJson)?.groupValues?.get(1)
            ?.replace("\\/", "/")?.replace("\\\\", "\\")
    fun shortPath(p: String?) = p?.substringAfterLast('/')?.take(40)
    return when (name) {
        "Read" -> "Читаю ${shortPath(field("file_path")) ?: "файл"}"
        "Write" -> "Пишу ${shortPath(field("file_path")) ?: "файл"}"
        "Edit" -> "Правлю ${shortPath(field("file_path")) ?: "файл"}"
        "Bash" -> "Запускаю: ${(field("description") ?: field("command"))?.take(48) ?: "команду"}"
        "Grep" -> "Ищу: ${field("pattern")?.take(30) ?: ""}"
        "Glob" -> "Ищу файлы: ${field("pattern")?.take(30) ?: ""}"
        "WebSearch" -> "Ищу в сети: ${field("query")?.take(40) ?: ""}"
        "WebFetch" -> "Открываю: ${field("url")?.take(44) ?: "страницу"}"
        "TodoWrite" -> "Обновляю план"
        "Task", "Agent" -> "Запускаю агента: ${field("description")?.take(40) ?: ""}"
        else -> "$name…"
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) { Text(text, fontSize = 14.sp) }
    }
}

@Composable
private fun AssistantBubble(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
private fun CollapsibleRow(
    title: String, subtitle: String, error: Boolean = false, inProgress: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inProgress) {
                CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(7.dp))
            }
            Text(
                title, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "▲" else "▼", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun StatusFooter(vm: AppViewModel, chat: ChatState) {
    val ctx = chat.context
    val u = vm.usage
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ctx != null) {
            LinearProgressIndicator(
                progress = { (ctx.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${fmtTokens(ctx.totalTokens)} / ${fmtTokens(ctx.maxTokens)} (${ctx.percentage}%)",
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.weight(1f))
        if (u != null && u.available) {
            Text(
                "5ч ${u.fiveHourPct ?: "—"}% · 7д ${u.sevenDayPct ?: "—"}%",
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
    }
}

// 24076 -> "24.1k", 1000000 -> "1M"
private fun fmtTokens(n: Long): String = when {
    n >= 1_000_000 -> {
        val m = n / 1_000_000.0
        if (m % 1.0 < 0.05) "${m.toInt()}M" else String.format(java.util.Locale.US, "%.1fM", m)
    }
    n >= 1_000 -> {
        val k = n / 1_000.0
        if (k >= 100) "${k.toInt()}k" else String.format(java.util.Locale.US, "%.1fk", k)
    }
    else -> n.toString()
}

@Composable
private fun InputBar(vm: AppViewModel, tab: String, chat: ChatState) {
    var text by rememberSaveable(tab) { mutableStateOf("") }
    var slashOpen by remember { mutableStateOf(false) }
    var tuneOpen by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Лёгкая окантовка, чтобы читались как кнопки, но не бросались в глаза.
            // 36dp + отступ снизу 10dp = по центру однострочного поля ввода (56dp)
            val buttonOutline = Modifier.padding(bottom = 10.dp).size(36.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f), CircleShape)
            Box {
                IconButton(onClick = { slashOpen = true }, modifier = buttonOutline) {
                    Text(
                        "/", fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
                DropdownMenu(expanded = slashOpen, onDismissRequest = { slashOpen = false }) {
                    val cmds = vm.commands.take(30)
                    if (cmds.isEmpty()) DropdownMenuItem(text = { Text("Команды появятся после первого хода") }, onClick = { slashOpen = false })
                    for (c in cmds) DropdownMenuItem(
                        text = { Column {
                            Text("/${c.name}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            if (c.description.isNotBlank()) Text(c.description.take(60), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        } },
                        onClick = { text = "/${c.name} "; slashOpen = false },
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Box {
                IconButton(onClick = { tuneOpen = true }, modifier = buttonOutline) {
                    Icon(Icons.Filled.Tune, "Режим", Modifier.size(17.dp))
                }
                TuneMenu(vm, tab, tuneOpen) { tuneOpen = false }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Сообщение…") },
                modifier = Modifier.weight(1f),
                maxLines = 6,
                shape = RoundedCornerShape(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            if (chat.running) {
                IconButton(
                    onClick = { vm.interrupt(tab) },
                    modifier = Modifier.padding(bottom = 4.dp).size(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                ) { Icon(Icons.Filled.Stop, "Прервать", tint = MaterialTheme.colorScheme.error) }
            } else {
                IconButton(
                    onClick = {
                        val t = text.trim()
                        if (t.isNotEmpty()) { vm.sendMessage(tab, t); text = "" }
                    },
                    modifier = Modifier.padding(bottom = 4.dp).size(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        if (chat.queued > 0) {
            Text(
                "В очереди: ${chat.queued}", fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun TuneMenu(vm: AppViewModel, tab: String, open: Boolean, dismiss: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    DropdownMenu(expanded = open, onDismissRequest = dismiss) {
        Text("Effort (следующий ход)", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        for (e in listOf("low", "medium", "high", "xhigh", "max")) {
            DropdownMenuItem(text = { Text(e) }, onClick = {
                scope.launch { runCatching { vm.api?.saveSettings(tab, null, null, e) } }
                dismiss()
            })
        }
        Text("Режим прав", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        for ((mode, label) in listOf(
            "bypassPermissions" to "Всё разрешено",
            "acceptEdits" to "Авто-правки",
            "plan" to "План (без выполнения)",
        )) {
            DropdownMenuItem(text = { Text(label) }, onClick = {
                scope.launch { runCatching { vm.api?.saveSettings(tab, mode, null, null) } }
                dismiss()
            })
        }
    }
}
