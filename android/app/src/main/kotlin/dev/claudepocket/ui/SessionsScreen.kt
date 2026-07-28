package dev.claudepocket.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.claudepocket.AppViewModel
import dev.claudepocket.net.FolderInfo
import dev.claudepocket.net.SessionInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Строка списка: либо папка, либо сессия (внутри папки или в общем списке)
private sealed interface ListRow {
    data class Folder(
        val folder: FolderInfo, val count: Int, val running: Boolean, val expanded: Boolean,
    ) : ListRow

    data class Session(val s: SessionInfo, val inFolder: Boolean) : ListRow
}

@Composable
fun SessionsScreen(vm: AppViewModel) {
    // При каждом входе на экран (в том числе возврате из чата) подтягиваем список:
    // иначе названия и порядок остаются с момента подключения
    LaunchedEffect(Unit) {
        vm.refreshSessions()
        vm.refreshUsage()
        vm.loadFolders()
    }

    // Диалоги: создание папки (для сессии, если указана), переименование, удаление
    var createFolderFor by remember { mutableStateOf<String?>(null) }
    var createFolderOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FolderInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<FolderInfo?>(null) }
    var renameSessionFor by remember { mutableStateOf<SessionInfo?>(null) }

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.newTab() }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, "Новая сессия", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { pad ->
      Box(Modifier.fillMaxSize().padding(pad)) {
        Column(Modifier.fillMaxSize()) {
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
                IconButton(onClick = { createFolderFor = null; createFolderOpen = true }) {
                    Icon(Icons.Filled.CreateNewFolder, "Новая папка", Modifier.size(20.dp))
                }
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

            // Папки сверху (свёрнутые по умолчанию), ниже — сессии без папки.
            // Разворачиваются только вручную (тап по строке папки).
            val byFolder = vm.sessions.groupBy { vm.sessionFolder[it.id] }
            val rows = buildList {
                for (f in vm.folders) {
                    val inside = byFolder[f.id].orEmpty()
                    val expanded = f.id in vm.expandedFolders
                    add(ListRow.Folder(f, inside.size, inside.any { it.running }, expanded))
                    if (expanded) inside.forEach { add(ListRow.Session(it, inFolder = true)) }
                }
                byFolder[null].orEmpty().forEach { add(ListRow.Session(it, inFolder = false)) }
            }

            // Подъём списка наверх: при создании папки и когда сверху появляется
            // другой элемент (сессия поднялась по дате / новая папка)
            val listState = rememberLazyListState()
            val topKey = rows.firstOrNull()?.let {
                if (it is ListRow.Folder) "f:${it.folder.id}" else "s:${(it as ListRow.Session).s.id}"
            }
            LaunchedEffect(topKey, vm.folders.size) {
                if (topKey != null) listState.animateScrollToItem(0)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(
                    rows,
                    key = { r -> if (r is ListRow.Folder) "f:${r.folder.id}" else "s:${(r as ListRow.Session).s.id}" },
                ) { r ->
                    when (r) {
                        is ListRow.Folder -> FolderRow(
                            row = r,
                            onToggle = { vm.toggleFolder(r.folder.id) },
                            onRename = { renameTarget = r.folder },
                            onDelete = { deleteTarget = r.folder },
                        )
                        is ListRow.Session -> SessionCard(
                            s = r.s,
                            displayName = vm.sessionNames[r.s.id] ?: r.s.title,
                            inFolder = r.inFolder,
                            isOpen = r.s.id in vm.tabs,
                            folders = vm.folders,
                            currentFolderId = vm.sessionFolder[r.s.id],
                            onOpen = { vm.openTab(r.s.id) },
                            onMove = { folderId -> vm.moveSessionToFolder(r.s.id, folderId) },
                            onNewFolder = { createFolderFor = r.s.id; createFolderOpen = true },
                            onRename = { renameSessionFor = r.s },
                        )
                    }
                }
            }
            SessionsFooter(vm)
        }
        // Свайп от правого края влево — к последней открытой сессии (или верхней)
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(22.dp)
                .pointerInput(Unit) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            if (dx < -120f) {
                                val t = vm.tabs.lastOrNull()
                                if (t != null) vm.activeTab = t
                                else vm.sessions.firstOrNull()?.let { vm.openTab(it.id) }
                            }
                        },
                    ) { _, amount -> dx += amount }
                },
        )
      }
    }

    if (createFolderOpen) {
        NameDialog(
            title = if (createFolderFor == null) "Новая папка" else "Новая папка для сессии",
            initial = "",
            confirmLabel = "Создать",
            onDismiss = { createFolderOpen = false; createFolderFor = null },
            onConfirm = { name ->
                val id = vm.createFolder(name)
                val session = createFolderFor
                if (id != null && session != null) vm.moveSessionToFolder(session, id)
                createFolderOpen = false
                createFolderFor = null
            },
        )
    }

    renameTarget?.let { f ->
        NameDialog(
            title = "Переименовать папку",
            initial = f.name,
            confirmLabel = "Сохранить",
            onDismiss = { renameTarget = null },
            onConfirm = { name -> vm.renameFolder(f.id, name); renameTarget = null },
        )
    }

    deleteTarget?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить папку?") },
            text = { Text("«${f.name}» будет убрана. Сессии из неё вернутся в общий список — сами сессии не удаляются.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteFolder(f.id); deleteTarget = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }

    renameSessionFor?.let { s ->
        NameDialog(
            title = "Переименовать сессию",
            initial = vm.sessionNames[s.id] ?: s.title,
            confirmLabel = "Сохранить",
            onDismiss = { renameSessionFor = null },
            onConfirm = { name -> vm.renameSession(s.id, name); renameSessionFor = null },
        )
    }
}

// Свёрнутая папка — одна строка: значок, название, счётчик, меню
@Composable
private fun FolderRow(row: ListRow.Folder, onToggle: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onToggle)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (row.expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            row.folder.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        // Работающая сессия внутри свёрнутой папки — видно по точке
        if (row.running) {
            Box(Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            row.count.toString(), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.MoreVert, "Меню папки", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Удалить папку", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

// Карточка сессии. Открытая (во вкладках) подсвечена терракотовым акцентом.
@Composable
private fun SessionCard(
    s: SessionInfo,
    displayName: String,
    inFolder: Boolean,
    isOpen: Boolean,
    folders: List<FolderInfo>,
    currentFolderId: String?,
    onOpen: () -> Unit,
    onMove: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        ),
        border = if (isOpen) BorderStroke(1.dp, accent) else null,
        shape = RoundedCornerShape(18.dp),   // скруглены как пузыри чата
        modifier = Modifier.fillMaxWidth().padding(start = if (inFolder) 16.dp else 0.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 14.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.running) Box(
                        Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape)
                    )
                    if (s.running) Spacer(Modifier.size(6.dp))
                    Text(
                        displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        color = if (isOpen) accent else MaterialTheme.colorScheme.onSurface,
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
            Box(Modifier.padding(top = 4.dp, end = 2.dp)) {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.MoreVert, "Меню сессии", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Text(
                        "Переместить в папку", fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    for (f in folders) {
                        val selected = f.id == currentFolderId
                        DropdownMenuItem(
                            text = {
                                Text(
                                    f.name,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = {
                                if (selected) Icon(Icons.Filled.Check, null, tint = accent)
                                else Spacer(Modifier.size(24.dp))
                            },
                            onClick = { menuOpen = false; onMove(f.id) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Новая папка…") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(20.dp)) },
                        onClick = { menuOpen = false; onNewFolder() },
                    )
                    if (currentFolderId != null) {
                        DropdownMenuItem(
                            text = { Text("Убрать из папки") },
                            onClick = { menuOpen = false; onMove(null) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(20.dp)) },
                        onClick = { menuOpen = false; onRename() },
                    )
                }
            }
        }
    }
}

// Общий диалог ввода имени папки (создание и переименование)
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Название") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
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
