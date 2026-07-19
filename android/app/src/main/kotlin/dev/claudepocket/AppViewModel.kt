package dev.claudepocket

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.claudepocket.net.ApiClient
import dev.claudepocket.net.Block
import dev.claudepocket.net.ContextInfo
import dev.claudepocket.net.SessionInfo
import dev.claudepocket.net.SlashCommand
import dev.claudepocket.net.SseState
import dev.claudepocket.net.SshTunnel
import dev.claudepocket.net.UsageInfo
import dev.claudepocket.net.sseFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import dev.claudepocket.net.parseBlocks

sealed interface ConnState {
    object Disconnected : ConnState
    data class Connecting(val step: String) : ConnState
    object Connected : ConnState
    data class Failed(val error: String) : ConnState
}

// Элемент ленты чата
sealed interface ChatItem {
    data class Text(val role: String, val text: String, val key: String) : ChatItem
    data class Thinking(val text: String, val key: String) : ChatItem
    data class Tool(
        val id: String, val name: String, val input: String,
        val result: String? = null, val isError: Boolean = false, val key: String,
    ) : ChatItem
    data class SystemNote(val text: String, val key: String) : ChatItem
}

class ChatState {
    var items by mutableStateOf<List<ChatItem>>(emptyList())
    var streaming by mutableStateOf("")       // накапливаемый текст текущего ответа
    var running by mutableStateOf(false)
    var queued by mutableStateOf(0)
    var context by mutableStateOf<ContextInfo?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    // Сохранённые подключения (порядок = порядок карточек) и активное подключение
    var connections by mutableStateOf<List<ConnectionPrefs>>(emptyList())
        private set
    var active by mutableStateOf<ConnectionPrefs?>(null)
        private set

    // Навигация экрана входа: список ↔ форма (editing == null → новое подключение)
    var showForm by mutableStateOf(false)
    var editing by mutableStateOf<ConnectionPrefs?>(null)

    var conn by mutableStateOf<ConnState>(ConnState.Disconnected)
    var sessions by mutableStateOf<List<SessionInfo>>(emptyList())
    var sessionsLoading by mutableStateOf(false)
    var usage by mutableStateOf<UsageInfo?>(null)
    var commands by mutableStateOf<List<SlashCommand>>(emptyList())

    // Вкладки: ключ = sessionId либо temp 'new-...'
    var tabs by mutableStateOf<List<String>>(emptyList())
    var activeTab by mutableStateOf<String?>(null)
    val chats = mutableStateMapOf<String, ChatState>()

    // Обновления приложения
    var update by mutableStateOf<dev.claudepocket.net.UpdateInfo?>(null)
    var updateProgress by mutableStateOf<Int?>(null)
    var updateChecking by mutableStateOf(false)

    private var tunnel: SshTunnel? = null
    var api: ApiClient? = null; private set
    private var sseJob: Job? = null
    private var lastSeq = 0L

    // Тема оформления: система / тёмная / светлая (сохраняется на устройстве)
    var themeMode by mutableStateOf(
        runCatching {
            dev.claudepocket.ui.ThemeMode.valueOf(
                app.getSharedPreferences("ui", Application.MODE_PRIVATE).getString("themeMode", "SYSTEM") ?: "SYSTEM"
            )
        }.getOrDefault(dev.claudepocket.ui.ThemeMode.SYSTEM)
    )
        private set

    fun cycleTheme() {
        themeMode = when (themeMode) {
            dev.claudepocket.ui.ThemeMode.SYSTEM -> dev.claudepocket.ui.ThemeMode.LIGHT
            dev.claudepocket.ui.ThemeMode.LIGHT -> dev.claudepocket.ui.ThemeMode.DARK
            dev.claudepocket.ui.ThemeMode.DARK -> dev.claudepocket.ui.ThemeMode.SYSTEM
        }
        getApplication<Application>().getSharedPreferences("ui", Application.MODE_PRIVATE)
            .edit().putString("themeMode", themeMode.name).apply()
        toast(when (themeMode) {
            dev.claudepocket.ui.ThemeMode.SYSTEM -> "Тема: как в системе"
            dev.claudepocket.ui.ThemeMode.LIGHT -> "Тема: светлая"
            dev.claudepocket.ui.ThemeMode.DARK -> "Тема: тёмная"
        })
    }

    init {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { ConnectionStore.load(app) }
            connections = list
            // Первый запуск без сохранённых подключений — сразу открываем форму
            if (list.isEmpty()) showForm = true
        }
    }

    private fun persist() {
        val list = connections
        viewModelScope.launch(Dispatchers.IO) { ConnectionStore.save(getApplication(), list) }
    }

    // Добавить или обновить подключение в списке; возвращает вариант с заполненным id
    fun upsertConnection(c: ConnectionPrefs): ConnectionPrefs {
        val withId = if (c.id.isBlank()) c.copy(id = ConnectionStore.newId()) else c
        connections = if (connections.any { it.id == withId.id })
            connections.map { if (it.id == withId.id) withId else it }
        else connections + withId
        persist()
        return withId
    }

    fun deleteConnection(id: String) {
        connections = connections.filterNot { it.id == id }
        persist()
    }

    fun moveConnection(from: Int, to: Int) {
        if (from == to || from !in connections.indices || to !in connections.indices) return
        connections = connections.toMutableList().also { it.add(to, it.removeAt(from)) }
        persist()
    }

    // remember=false — разовое подключение: не сохраняем и не прописываем ключ на сервер
    fun connect(c0: ConnectionPrefs, remember: Boolean = true) {
        if (conn is ConnState.Connecting) return
        val p = if (remember) upsertConnection(c0) else c0
        active = p
        conn = ConnState.Connecting("SSH-подключение…")
        viewModelScope.launch {
            try {
                val known = java.io.File(getApplication<Application>().filesDir, "known_hosts")
                val t = withContext(Dispatchers.IO) {
                    tunnel?.disconnect()
                    SshTunnel(p, known, enroll = remember).also { it.connect() }
                }
                tunnel = t
                // Приложение прописало свой ключ на сервер — запоминаем приватную часть
                t.newDeviceKey?.let { k ->
                    val withKey = p.copy(deviceKey = k)
                    active = withKey
                    if (remember) upsertConnection(withKey)
                }
                t.enrollError?.let { toast("Ключ устройства не прописан ($it) — вход остаётся по паролю") }
                conn = ConnState.Connecting("Проверка демона…")
                val a = ApiClient("http://127.0.0.1:${t.localPort}", t.token)
                if (!a.health()) throw IllegalStateException("Демон не отвечает на порту ${p.daemonPort}. Установлен ли claude-pocketd?")
                api = a
                conn = ConnState.Connected
                showForm = false
                editing = null
                startSse()
                refreshSessions()
                refreshUsage()
                viewModelScope.launch {
                    runCatching { commands = a.commands() }
                }
                viewModelScope.launch {
                    runCatching { update = dev.claudepocket.net.UpdateChecker.checkIfDue(getApplication()) }
                }
            } catch (e: Exception) {
                conn = ConnState.Failed(e.message ?: e.toString())
                tunnel?.disconnect()
            }
        }
    }

    private fun reconnect() {
        active?.let { connect(it, remember = it.id.isNotBlank()) }
    }

    fun disconnect() {
        sseJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { tunnel?.disconnect() }
        api = null
        conn = ConnState.Disconnected
    }

    // Проверка обновлений по кнопке — без суточного интервала, результат тостом внизу экрана
    fun checkUpdates() {
        if (updateChecking) return
        viewModelScope.launch {
            updateChecking = true
            try {
                val info = dev.claudepocket.net.UpdateChecker.check()
                if (info != null) update = info
                else toast("Установлена последняя версия (${BuildConfig.VERSION_NAME})")
            } catch (e: Exception) {
                toast("Не удалось проверить обновления: ${e.message ?: "нет сети"}")
            }
            updateChecking = false
        }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_LONG).show()
    }

    fun installUpdate() {
        val info = update ?: return
        if (updateProgress != null) return
        viewModelScope.launch {
            updateProgress = 0
            try {
                dev.claudepocket.net.UpdateChecker.downloadAndInstall(getApplication(), info) { updateProgress = it }
            } catch (_: Exception) { /* остаёмся на плашке — можно повторить */ }
            updateProgress = null
        }
    }

    // Сбросить сохранённый отпечаток сервера (после переустановки сервера)
    fun forgetHostKey() {
        java.io.File(getApplication<Application>().filesDir, "known_hosts").delete()
        if (conn is ConnState.Failed) conn = ConnState.Disconnected
    }

    private fun startSse() {
        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            while (true) {
                val a = api ?: break
                try {
                    sseFlow(a) { lastSeq }.collect { st ->
                        when (st) {
                            is SseState.Event -> { lastSeq = maxOf(lastSeq, st.ev.seq); onEvent(st.ev.type, st.ev.session, st.ev.data) }
                            is SseState.Closed -> throw RuntimeException(st.error ?: "closed")
                            else -> {}
                        }
                    }
                } catch (_: Exception) {
                    // Обрыв — SSH мог умереть. Пробуем переподключить туннель целиком.
                    if (conn is ConnState.Connected) {
                        val alive = withContext(Dispatchers.IO) { tunnel?.isConnected == true && api?.health() == true }
                        if (!alive) { conn = ConnState.Disconnected; reconnect(); break }
                    } else break
                }
                delay(2000)
            }
        }
    }

    private fun onEvent(type: String, session: String, data: kotlinx.serialization.json.JsonObject) {
        val chat = chats[session] ?: chats[tabForSession(session)] ?: run {
            if (type == "usage") parseUsage(data)
            return
        }
        when (type) {
            "session.created" -> {
                val tempKey = data["tempKey"]?.jsonPrimitive?.contentOrNull ?: return
                remapTab(tempKey, session)
            }
            "job.queued" -> { chat.queued++ }
            "job.started" -> { chat.running = true; if (chat.queued > 0) chat.queued--; chat.streaming = "" }
            "delta" -> {
                chat.streaming += data["text"]?.jsonPrimitive?.contentOrNull ?: ""
            }
            "assistant" -> {
                val blocks = parseBlocks(data["blocks"]!!.jsonArray)
                chat.streaming = ""
                val add = mutableListOf<ChatItem>()
                for (b in blocks) when (b.type) {
                    "text" -> add += ChatItem.Text("assistant", b.text, keyOf())
                    "thinking" -> add += ChatItem.Thinking(b.text, keyOf())
                    "tool_use" -> add += ChatItem.Tool(b.id, b.name, b.input, key = keyOf())
                }
                chat.items = chat.items + add
            }
            "tool_result" -> {
                val blocks = parseBlocks(data["blocks"]!!.jsonArray)
                var items = chat.items
                for (b in blocks) {
                    val idx = items.indexOfLast { it is ChatItem.Tool && it.id == b.toolUseId }
                    if (idx >= 0) {
                        val t = items[idx] as ChatItem.Tool
                        items = items.toMutableList().also {
                            it[idx] = t.copy(result = b.text, isError = b.isError)
                        }
                    }
                }
                chat.items = items
            }
            "job.done" -> {
                chat.running = false
                chat.streaming = ""
                val ok = data["ok"]?.jsonPrimitive?.booleanOrNull ?: true
                if (!ok) {
                    val sub = data["subtype"]?.jsonPrimitive?.contentOrNull ?: "ошибка"
                    chat.items = chat.items + ChatItem.SystemNote("Ход завершился с ошибкой: $sub", keyOf())
                }
                refreshSessionsSoon()
            }
            "job.interrupted" -> {
                chat.running = false; chat.streaming = ""
                chat.items = chat.items + ChatItem.SystemNote("Прервано", keyOf())
            }
            "job.error" -> {
                chat.running = false; chat.streaming = ""
                chat.items = chat.items + ChatItem.SystemNote("Ошибка: ${data["error"]?.jsonPrimitive?.contentOrNull}", keyOf())
            }
            "turn.compacted" -> {
                chat.items = chat.items + ChatItem.SystemNote("Контекст сжат (/compact)", keyOf())
            }
            "usage" -> parseUsage(data)
            "context" -> {
                val c = data["context"]?.jsonObject ?: return
                chat.context = ContextInfo(
                    totalTokens = c["totalTokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
                    maxTokens = c["maxTokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 1,
                    percentage = c["percentage"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0,
                    model = c["model"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            }
        }
    }

    private fun parseUsage(data: kotlinx.serialization.json.JsonObject) {
        val u = data["usage"]?.jsonObject ?: return
        fun pct(key: String) = (u[key] as? kotlinx.serialization.json.JsonObject)
            ?.get("utilization")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
        fun res(key: String) = (u[key] as? kotlinx.serialization.json.JsonObject)
            ?.get("resets_at")?.jsonPrimitive?.contentOrNull
        usage = UsageInfo(
            available = u["available"]?.jsonPrimitive?.booleanOrNull ?: false,
            fiveHourPct = pct("fiveHour"), fiveHourResets = res("fiveHour"),
            sevenDayPct = pct("sevenDay"), sevenDayResets = res("sevenDay"),
        )
    }

    private var keyCounter = 0
    private fun keyOf() = "live-${++keyCounter}"

    private fun tabForSession(session: String): String =
        tabs.firstOrNull { it == session } ?: session

    private fun remapTab(tempKey: String, sessionId: String) {
        tabs = tabs.map { if (it == tempKey) sessionId else it }
        if (activeTab == tempKey) activeTab = sessionId
        chats.remove(tempKey)?.let { chats[sessionId] = it }
    }

    fun refreshSessions() {
        val a = api ?: return
        viewModelScope.launch {
            sessionsLoading = true
            runCatching { sessions = a.sessions() }
            sessionsLoading = false
        }
    }

    private var refreshScheduled = false
    private fun refreshSessionsSoon() {
        if (refreshScheduled) return
        refreshScheduled = true
        viewModelScope.launch { delay(1500); refreshScheduled = false; refreshSessions() }
    }

    fun refreshUsage() {
        val a = api ?: return
        viewModelScope.launch { runCatching { usage = a.usage() } }
    }

    fun openTab(sessionId: String) {
        if (sessionId !in tabs) tabs = tabs + sessionId
        activeTab = sessionId
        if (chats[sessionId] == null) {
            chats[sessionId] = ChatState()
            loadHistory(sessionId)
        }
    }

    fun newTab() {
        val key = "new-" + java.util.UUID.randomUUID()
        tabs = tabs + key
        activeTab = key
        chats[key] = ChatState().also { it.loading = false; it.title = "Новая сессия" }
    }

    fun closeTab(key: String) {
        tabs = tabs - key
        chats.remove(key)
        if (activeTab == key) activeTab = tabs.lastOrNull()
    }

    fun loadHistory(sessionId: String) {
        val a = api ?: return
        val chat = chats[sessionId] ?: return
        viewModelScope.launch {
            chat.loading = true
            try {
                val (_, items) = a.history(sessionId)
                val list = mutableListOf<ChatItem>()
                var k = 0
                for (h in items) for (b in h.blocks) {
                    val key = "h-${h.uuid}-${k++}"
                    when (b.type) {
                        "text" -> list += ChatItem.Text(if (h.role == "assistant") "assistant" else "user", b.text, key)
                        "thinking" -> list += ChatItem.Thinking(b.text, key)
                        "tool_use" -> list += ChatItem.Tool(b.id, b.name, b.input, key = key)
                        "tool_result" -> {
                            val idx = list.indexOfLast { it is ChatItem.Tool && it.id == b.toolUseId }
                            if (idx >= 0) {
                                val t = list[idx] as ChatItem.Tool
                                list[idx] = t.copy(result = b.text, isError = b.isError)
                            }
                        }
                    }
                }
                chat.items = list
                chat.title = sessions.firstOrNull { it.id == sessionId }?.title ?: ""
                chat.context = runCatching { a.context(sessionId) }.getOrNull()
            } catch (e: Exception) {
                chat.error = e.message
            }
            chat.loading = false
        }
    }

    fun sendMessage(tabKey: String, text: String, attachments: List<kotlinx.serialization.json.JsonObject> = emptyList()) {
        val a = api ?: return
        val chat = chats[tabKey] ?: return
        chat.items = chat.items + ChatItem.Text("user", text, keyOf())
        chat.running = true
        viewModelScope.launch {
            try {
                val isNew = tabKey.startsWith("new-")
                val (_, sessionKey) = a.send(if (isNew) null else tabKey, text, attachments)
                if (isNew && sessionKey != tabKey) {
                    // Демон вернул свой temp-ключ; события session.created придёт с реальным id
                    remapTab(tabKey, sessionKey)
                }
            } catch (e: Exception) {
                chat.running = false
                chat.items = chat.items + ChatItem.SystemNote("Не отправлено: ${e.message}", keyOf())
            }
        }
    }

    fun interrupt(tabKey: String) {
        val a = api ?: return
        viewModelScope.launch { runCatching { a.interrupt(tabKey) } }
    }

    override fun onCleared() {
        tunnel?.disconnect()
    }
}
