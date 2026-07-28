package dev.claudepocket.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.claudepocket.AppViewModel
import dev.claudepocket.ChatItem
import dev.claudepocket.ChatState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ChatScreen(vm: AppViewModel) {
    val tab = vm.activeTab ?: return
    val chat = vm.chats[tab] ?: return
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val bg = MaterialTheme.colorScheme.background
    // Список едет под панелями (Telegram-style), поэтому высоты шапки и низа
    // замеряем и отдаём в contentPadding — сообщения не обрезаются за интерфейсом.
    var topBarPx by remember { mutableStateOf(0) }
    var bottomBarPx by remember { mutableStateOf(0) }
    val topPad = with(density) { topBarPx.toDp() }
    val bottomPad = with(density) { bottomBarPx.toDp() }

    Box(Modifier.fillMaxSize()) {
        // ── Лента на всю высоту, под полупрозрачными панелями ──
        // key(tab) — состояние прокрутки своё у каждой вкладки
        key(tab) {
            val listState = rememberLazyListState()
            val total = chat.items.size + (if (chat.streaming.isNotBlank()) 1 else 0)
            // Пользователь и так внизу?
            val atBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                    last.index >= info.totalItemsCount - 2
                }
            }
            // Вход в диалог — сразу к последнему сообщению (мгновенно, один раз)
            var didInitial by remember { mutableStateOf(false) }
            LaunchedEffect(chat.loading, total) {
                if (!didInitial && !chat.loading && total > 0) {
                    listState.scrollToItem(total - 1); didInitial = true
                }
            }
            // Дальше автопрокрутка — только если пользователь внизу
            LaunchedEffect(total, chat.streaming.length / 200) {
                if (didInitial && total > 0 && atBottom) listState.animateScrollToItem(total - 1)
            }

            when {
                chat.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    MessageList(chat, listState, topPad, bottomPad)
                    // Скроллбар — только в видимой зоне между панелями
                    ChatScrollbar(
                        listState,
                        Modifier.align(Alignment.CenterEnd).padding(top = topPad, bottom = bottomPad),
                    )
                    // Кнопка «в самый низ» — когда список не внизу; над панелью ввода
                    if (!atBottom && total > 1) {
                        FilledIconButton(
                            onClick = { scope.launch { listState.animateScrollToItem(total - 1) } },
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = bottomPad + 16.dp).size(40.dp),
                        ) { Icon(Icons.Filled.KeyboardArrowDown, "В самый низ", Modifier.size(22.dp)) }
                    }
                }
            }
        }

        // ── Шапка поверх: полупрозрачная, к списку затухает; тач под ней в ленту
        // не проходит (blockTouches гасит касания на всей площади панели) ──
        Box(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .onSizeChanged { topBarPx = it.height }
                .background(Brush.verticalGradient(
                    0f to bg.copy(alpha = 0.92f), 0.7f to bg.copy(alpha = 0.92f), 1f to bg.copy(alpha = 0f),
                ))
                .blockTouches()
                .statusBarsPadding(),
        ) { TabsBar(vm) }

        // ── Низ поверх: футер + опросник + ввод; фон затухает вверх к списку ──
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .onSizeChanged { bottomBarPx = it.height }
                .background(Brush.verticalGradient(
                    0f to bg.copy(alpha = 0f), 0.3f to bg.copy(alpha = 0.92f), 1f to bg.copy(alpha = 0.92f),
                ))
                .blockTouches()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            StatusFooter(vm, chat)
            if (chat.pendingQuestions.isNotEmpty()) {
                QuestionCard(chat.pendingQuestions) { answers -> vm.answerQuestion(tab, answers) }
            }
            InputBar(vm, tab, chat)
        }
    }
}

// Гасит касания на всей площади композита: нажатия по прозрачной зоне панели
// не проваливаются в ленту под ней. Интерактивные дети (кнопки, поле, скролл
// чипов) получают событие раньше — им это не мешает.
private fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

// Карточка живого опросника модели: вопросы показываем по одному, ответы копим,
// на последнем — отправляем все разом. «Свой ответ» = вариант Other.
@Composable
private fun QuestionCard(questions: List<dev.claudepocket.net.PocketQuestion>, onSubmit: (Map<String, Any>) -> Unit) {
    var index by remember(questions) { mutableStateOf(0) }
    val answers = remember(questions) { mutableStateMapOf<String, Any>() }
    val q = questions.getOrNull(index) ?: return
    val selected = remember(index) { mutableStateListOf<String>() }
    var custom by rememberSaveable(index) { mutableStateOf("") }

    fun commit(answer: Any) {
        answers[q.question] = answer
        if (index < questions.lastIndex) index++ else onSubmit(answers.toMap())
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        if (questions.size > 1) Text(
            "Вопрос ${index + 1} из ${questions.size}", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        if (q.header.isNotBlank()) Text(
            q.header.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(q.question, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

        if (q.multiSelect) {
            for (opt in q.options) {
                val on = opt.label in selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { if (on) selected.remove(opt.label) else selected.add(opt.label) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(checked = on, onCheckedChange = {
                        if (on) selected.remove(opt.label) else selected.add(opt.label)
                    })
                    QOptionText(opt)
                }
            }
            OutlinedTextField(
                custom, { custom = it }, label = { Text("Свой ответ (необязательно)") },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
            )
            androidx.compose.material3.Button(
                onClick = {
                    val list = selected.toMutableList()
                    if (custom.isNotBlank()) list.add(custom.trim())
                    commit(list.toList())
                },
                enabled = selected.isNotEmpty() || custom.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (index < questions.lastIndex) "Далее" else "Готово") }
        } else {
            for (opt in q.options) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { commit(opt.label) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) { QOptionText(opt, Modifier.weight(1f)) }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    custom, { custom = it }, label = { Text("Свой ответ") },
                    modifier = Modifier.weight(1f), singleLine = true,
                )
                IconButton(onClick = { if (custom.isNotBlank()) commit(custom.trim()) }, enabled = custom.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить свой ответ")
                }
            }
        }
    }
}

@Composable
private fun QOptionText(opt: dev.claudepocket.net.QOption, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp)) {
        Text(opt.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (opt.description.isNotBlank()) Text(
            opt.description, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

// Тонкий скроллбар справа: заметен при прокрутке, при нажатии расширяется и тянется.
// Широкая прозрачная зона захвата (22dp) — чтобы можно было ухватить пальцем.
@Composable
private fun ChatScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val metrics by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            Triple(info.totalItemsCount, info.visibleItemsInfo.size, listState.firstVisibleItemIndex)
        }
    }
    val (total, visible, first) = metrics
    if (total == 0 || visible >= total) return
    // Виден слабо всегда (чтобы было за что взяться), ярче при прокрутке, ещё ярче при захвате
    val alpha by animateFloatAsState(
        if (dragging) 0.7f else if (listState.isScrollInProgress) 0.4f else 0.16f, label = "sbAlpha",
    )
    val barW by animateDpAsState(if (dragging) 10.dp else 4.dp, label = "sbWidth")
    var trackH by remember { mutableStateOf(1) }
    val thumbFrac = (visible.toFloat() / total).coerceIn(0.06f, 1f)
    val span = (total - visible).coerceAtLeast(1)
    val posFrac = (first.toFloat() / span).coerceIn(0f, 1f)

    fun scrollToFrac(f: Float) {
        val idx = (f.coerceIn(0f, 1f) * span).roundToInt().coerceIn(0, total - 1)
        scope.launch { listState.scrollToItem(idx) }
    }

    // Зона захвата 22dp; перетаскивание где угодно по ней двигает бегунок
    Box(
        modifier.fillMaxHeight().width(22.dp).padding(vertical = 4.dp)
            .onSizeChanged { trackH = it.height }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    val denom = trackH * (1f - thumbFrac)
                    if (denom > 0f) scrollToFrac(posFrac + delta / denom)
                },
                onDragStarted = { dragging = true },
                onDragStopped = { dragging = false },
            ),
    ) {
        Box(
            Modifier
                .fillMaxHeight(thumbFrac)
                .width(barW)
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, ((trackH * (1f - thumbFrac)) * posFrac).roundToInt()) }
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)),
        )
    }
}

@Composable
private fun TabsBar(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val nav = LocalPagerNav.current
        IconButton(onClick = { nav.toList() }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "К списку", modifier = Modifier.size(20.dp))
        }
        for (t in vm.tabs) {
            val active = t == vm.activeTab
            // Имя из списка сессий (там уже custom-title) — в приоритете, чтобы было одно везде
            val title = vm.sessions.firstOrNull { it.id == t }?.title
                ?: vm.chats[t]?.title?.ifBlank { null }
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
private fun MessageList(
    chat: ChatState, listState: androidx.compose.foundation.lazy.LazyListState,
    topPad: Dp, bottomPad: Dp,
) {
    // Оборачиваем в SelectionContainer — иначе текст сообщений нельзя выделить и скопировать
    SelectionContainer(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Верх/низ = высота панелей: первое и последнее сообщение полностью
            // выезжают из-под шапки и панели ввода, а не прячутся за ними
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = topPad + 8.dp, bottom = bottomPad + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chat.items, key = { it.itemKey }) { item -> ChatItemView(item) }
            if (chat.streaming.isNotBlank()) {
                item(key = "streaming") { AssistantBubble { MarkdownText(chat.streaming) } }
            } else if (chat.running) {
                item(key = "typing") {
                    // Индикатор набора выделять незачем — исключаем из копирования
                    DisableSelection {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Работаю…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
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
                // Скруглено со всех сторон, маленький «хвостик» у нижнего угла со стороны отправителя
                .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) { Text(text, fontSize = 14.sp) }
    }
}

@Composable
private fun AssistantBubble(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
            // Чуть темнее фона — ответы ассистента различимее (цвет в теме)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 9.dp),
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
        // Слева — лимит контекста; лимиты usage по центру места справа от него
        // (между распорками), поэтому на счётчик контекста не наезжают.
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
        Spacer(Modifier.weight(1f))
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

// Компактная квадратная кнопка панели ввода (без навязанного тач-таргета IconButton).
// Размер задаётся снаружи — панель масштабирует кнопки под ширину экрана.
@Composable
private fun SquareBtn(
    onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun InputBar(vm: AppViewModel, tab: String, chat: ChatState) {
    var text by rememberSaveable(tab) { mutableStateOf("") }
    var slashOpen by remember { mutableStateOf(false) }
    var tuneOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.addAttachment(tab, it) } }
    val attachments = vm.pendingAttachments[tab] ?: emptyList()

    Column {
        if (attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attachments.forEachIndexed { i, att ->
                    AssistChip(
                        onClick = { vm.removeAttachment(tab, i) },
                        label = { Text(att.name.take(24), fontSize = 12.sp, maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                if (att.isImage) Icons.Filled.Image else Icons.AutoMirrored.Filled.InsertDriveFile,
                                null, Modifier.size(16.dp),
                            )
                        },
                        trailingIcon = { Icon(Icons.Filled.Close, "Убрать", Modifier.size(16.dp)) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Компактные квадратные кнопки. Не через IconButton — он навязывает
            // минимальный тач-таргет 48dp и кнопки наезжают друг на друга.
            // Базовые 28dp/16dp масштабируются под ширину экрана (360dp — эталон),
            // с ограничением снизу и сверху, чтобы на планшетах не разъезжались.
            val scale = (LocalConfiguration.current.screenWidthDp / 360f).coerceIn(0.9f, 1.4f)
            val btnSize = (28f * scale).dp
            val iconSize = (16f * scale).dp
            // Отступ снизу центрует кнопку по однострочному полю ввода (56dp): 28 − половина кнопки
            val btnMod = Modifier.padding(bottom = (28f - 14f * scale).dp)
            SquareBtn(onClick = { picker.launch("*/*") }, modifier = btnMod, size = btnSize) {
                Icon(Icons.Filled.AttachFile, "Прикрепить файл", Modifier.size(iconSize))
            }
            Spacer(Modifier.width(6.dp))
            Box {
                SquareBtn(onClick = { slashOpen = true }, modifier = btnMod, size = btnSize) {
                    Text(
                        "/", fontSize = (15f * scale).sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
                AppMenu(expanded = slashOpen, onDismissRequest = { slashOpen = false }) {
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
                SquareBtn(onClick = { tuneOpen = true }, modifier = btnMod, size = btnSize) {
                    Icon(Icons.Filled.Tune, "Режим", Modifier.size(iconSize))
                }
                TuneMenu(vm, tab, tuneOpen) { tuneOpen = false }
            }
            Spacer(Modifier.width(8.dp))
            val awaitingAnswer = chat.pendingQuestions.isNotEmpty()
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(if (awaitingAnswer) "Ответьте на вопрос выше…" else "Сообщение…") },
                enabled = !awaitingAnswer,
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
                val canSend = (text.isNotBlank() || attachments.isNotEmpty()) && !awaitingAnswer
                IconButton(
                    onClick = {
                        val t = text.trim()
                        if (canSend) { vm.sendMessage(tab, t); text = "" }
                    },
                    enabled = canSend,
                    modifier = Modifier.padding(bottom = 4.dp).size(48.dp).clip(RoundedCornerShape(24.dp))
                        .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
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
    val chat = vm.chats[tab] ?: return
    val efforts = listOf("low", "medium", "high", "xhigh", "max")
    val effortLabels = mapOf(
        "low" to "Низкий", "medium" to "Средний", "high" to "Высокий",
        "xhigh" to "Очень высокий", "max" to "Максимум",
    )
    val modes = listOf(
        "bypassPermissions" to "Всё разрешено",
        "acceptEdits" to "Авто-правки",
        "plan" to "План (без выполнения)",
    )
    // null — модель по умолчанию (как в CLI); остальные — псевдонимы, их понимает SDK
    val models = listOf(
        null to "По умолчанию",
        "opus" to "Opus",
        "sonnet" to "Sonnet",
        "haiku" to "Haiku",
    )

    AppMenu(expanded = open, onDismissRequest = dismiss) {
        // Effort ползунком с пунктами; подпись уровня меняется над ним
        val idx = efforts.indexOf(chat.effort).coerceAtLeast(0)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).width(240.dp)) {
            Row {
                Text("Уровень усилий: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(effortLabels[chat.effort] ?: chat.effort, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = idx.toFloat(),
                onValueChange = { vm.setEffort(tab, efforts[it.toInt()]) },
                valueRange = 0f..(efforts.size - 1).toFloat(),
                steps = efforts.size - 2,   // промежуточные засечки между краями
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Низкий", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                Text("Максимум", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            }
        }
        HorizontalDivider()
        Text("Режим прав", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        for ((mode, label) in modes) {
            val selected = chat.permissionMode == mode
            DropdownMenuItem(
                text = {
                    Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                },
                leadingIcon = {
                    if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    else Spacer(Modifier.size(24.dp))
                },
                onClick = { vm.setPermissionMode(tab, mode); dismiss() },
            )
        }
        HorizontalDivider()
        Text("Модель", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        for ((id, label) in models) {
            val selected = chat.model == id
            DropdownMenuItem(
                text = {
                    Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                },
                leadingIcon = {
                    if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    else Spacer(Modifier.size(24.dp))
                },
                onClick = { vm.setModel(tab, id); dismiss() },
            )
        }
    }
}
