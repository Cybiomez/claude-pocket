package dev.claudepocket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.claudepocket.AppViewModel
import dev.claudepocket.ConnState
import dev.claudepocket.ConnectionPrefs

// Экран входа: список сохранённых подключений либо форма (новое/редактирование)
@Composable
fun SetupScreen(vm: AppViewModel) {
    if (vm.showForm) ConnectionForm(vm) else ConnectionList(vm)
}

// ---------- Список подключений ----------

@Composable
private fun ConnectionList(vm: AppViewModel) {
    val conn = vm.conn

    Column(
        Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Claude Pocket", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Сохранённые подключения",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp,
                )
            }
            val uriHandler = LocalUriHandler.current
            IconButton(onClick = { uriHandler.openUri(dev.claudepocket.net.UpdateChecker.REPO_URL) }) {
                Icon(
                    GitHubIcon, "Репозиторий на GitHub", Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
            }
            UpdateCheckButton(vm)
        }
        UpdateBanner(vm)

        if (conn is ConnState.Connecting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(conn.step, fontSize = 13.sp)
            }
        }
        if (conn is ConnState.Failed) {
            Text("Ошибка: ${conn.error}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            if (conn.error.contains("Ключ сервера изменился")) {
                Button(onClick = { vm.forgetHostKey() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Это точно мой сервер — забыть старый отпечаток")
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        if (vm.connections.isEmpty()) {
            Text(
                "Пока нет сохранённых подключений",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 14.sp,
            )
        } else {
            ReorderableConnectionCards(vm)
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { vm.editing = null; vm.showForm = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Новое подключение")
        }
    }
}

// Карточки с перетаскиванием: удержание — карточки покачиваются,
// удерживаемую можно перетащить; порядок сохраняется на устройстве.
@Composable
private fun ReorderableConnectionCards(vm: AppViewModel) {
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val angle by wiggle.animateFloat(
        -0.7f, 0.7f,
        infiniteRepeatable(tween(150), RepeatMode.Reverse), label = "angle",
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.connections.forEach { c ->
            key(c.id) {
                val isDragged = dragId == c.id
                ConnectionCard(
                    c = c,
                    enabled = vm.conn !is ConnState.Connecting,
                    onClick = { vm.connect(c) },
                    onEdit = { vm.editing = c; vm.showForm = true },
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            if (isDragged) {
                                translationY = dragOffset
                                scaleX = 1.02f; scaleY = 1.02f
                            } else if (dragId != null) {
                                rotationZ = angle
                            }
                        }
                        .onSizeChanged { heights[c.id] = it.height }
                        .pointerInput(c.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragId = c.id; dragOffset = 0f },
                                onDragEnd = { dragId = null; dragOffset = 0f },
                                onDragCancel = { dragId = null; dragOffset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val list = vm.connections
                                    val idx = list.indexOfFirst { it.id == c.id }
                                    if (idx < 0) return@detectDragGesturesAfterLongPress
                                    val spacing = 8.dp.toPx()
                                    if (dragOffset > 0 && idx < list.lastIndex) {
                                        val step = (heights[list[idx + 1].id] ?: 0) + spacing
                                        if (step > 0 && dragOffset > step / 2) {
                                            vm.moveConnection(idx, idx + 1); dragOffset -= step
                                        }
                                    } else if (dragOffset < 0 && idx > 0) {
                                        val step = (heights[list[idx - 1].id] ?: 0) + spacing
                                        if (step > 0 && dragOffset < -step / 2) {
                                            vm.moveConnection(idx, idx - 1); dragOffset += step
                                        }
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    c: ConnectionPrefs,
    enabled: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.DragIndicator, "Перетащить",
                Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.deviceKey.isNotBlank() || c.authType == "key") {
                        Icon(
                            Icons.Filled.Key, null, Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    val auth = when {
                        c.deviceKey.isNotBlank() -> "вход по ключу устройства"
                        c.authType == "key" -> "вход по SSH-ключу"
                        else -> "вход по паролю"
                    }
                    val prefix = if (c.name.isNotBlank()) "${c.user}@${c.host} · " else ""
                    Text(
                        prefix + auth, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit, "Редактировать", Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
    }
}

// ---------- Форма нового подключения / редактирования ----------

@Composable
private fun ConnectionForm(vm: AppViewModel) {
    val initial = vm.editing
    val isEdit = initial != null

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var authType by remember { mutableStateOf(initial?.authType ?: "password") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var privateKey by remember { mutableStateOf(initial?.privateKey ?: "") }
    var passphrase by remember { mutableStateOf(initial?.keyPassphrase ?: "") }
    var rememberConn by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }

    val conn = vm.conn
    val canGoBack = vm.connections.isNotEmpty() || isEdit
    val close = { vm.showForm = false; vm.editing = null }
    BackHandler(enabled = canGoBack) { close() }

    // Собрать подключение из полей. Если сменили сервер или пользователя —
    // старый ключ устройства к ним не подходит, сбрасываем.
    fun built(): ConnectionPrefs {
        val base = initial ?: ConnectionPrefs()
        val deviceKey =
            if (base.host == host.trim() && base.user == user.trim()) base.deviceKey else ""
        return base.copy(
            name = name.trim(), host = host.trim(), port = port.toIntOrNull() ?: 22,
            user = user.trim(), authType = authType, password = password,
            privateKey = privateKey, keyPassphrase = passphrase, deviceKey = deviceKey,
        )
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            if (isEdit) "Подключение" else "Новое подключение",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "Подключение к своему серверу с Claude Code по SSH",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it }, label = { Text("Адрес сервера") }, modifier = Modifier.weight(2f), singleLine = true)
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Порт") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp), singleLine = true)
        }
        OutlinedTextField(user, { user = it }, label = { Text("Пользователь") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = authType == "password", onClick = { authType = "password" }, label = { Text("Пароль") })
            FilterChip(selected = authType == "key", onClick = { authType = "key" }, label = { Text("SSH-ключ") })
        }

        if (authType == "password") {
            OutlinedTextField(
                password, { password = it }, label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        } else {
            OutlinedTextField(
                privateKey, { privateKey = it },
                label = { Text("Приватный ключ (текст целиком)") },
                placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----…") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            OutlinedTextField(
                passphrase, { passphrase = it }, label = { Text("Пароль ключа (если есть)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }

        if (!isEdit) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(checked = rememberConn, onCheckedChange = { rememberConn = it })
                Column {
                    Text("Запомнить подключение", fontSize = 14.sp)
                    Text(
                        if (rememberConn) "Появится в списке, вход дальше по ключу"
                        else "Разовый вход: без сохранения и без ключа на сервере",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
            }
        }
        OutlinedTextField(
            name, { name = it },
            label = { Text("Имя (необязательно)") },
            placeholder = { Text(user.ifBlank { "user" } + "@" + host.ifBlank { "server" }) },
            enabled = isEdit || rememberConn,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )

        if (conn is ConnState.Failed) {
            Text("Ошибка: ${conn.error}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            if (conn.error.contains("Ключ сервера изменился")) {
                Button(onClick = { vm.forgetHostKey() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Это точно мой сервер — забыть старый отпечаток")
                }
            }
        }

        val fieldsOk = host.isNotBlank() && user.isNotBlank()
        Button(
            onClick = { vm.connect(built(), remember = isEdit || rememberConn) },
            enabled = conn !is ConnState.Connecting && fieldsOk,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (conn is ConnState.Connecting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
                Text(conn.step)
            } else Text("Подключиться")
        }

        if (isEdit) {
            OutlinedButton(
                onClick = { vm.upsertConnection(built()); close() },
                enabled = fieldsOk,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Сохранить без подключения") }
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Удалить подключение", color = MaterialTheme.colorScheme.error)
            }
        } else if (canGoBack) {
            TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Назад к списку") }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить подключение?") },
            text = { Text("«${initial.displayName}» будет убрано из списка. Ключ устройства, прописанный на сервере, останется там.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteConnection(initial.id)
                    close()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}
