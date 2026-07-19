package dev.claudepocket.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

val json = Json { ignoreUnknownKeys = true; isLenient = true }

// --- модели (парсим вручную из JsonObject — API маленький, зато без сюрпризов) ---

data class SessionInfo(
    val id: String,
    val title: String,
    val lastText: String,
    val mtime: Long,
    val messageCount: Int,
    val running: Boolean,
    val queued: Int,
)

data class Block(
    val type: String,          // text | thinking | tool_use | tool_result
    val text: String = "",
    val id: String = "",
    val name: String = "",
    val input: String = "",    // JSON инструмента одной строкой
    val toolUseId: String = "",
    val isError: Boolean = false,
)

data class HistoryItem(val uuid: String, val ts: Long, val role: String, val blocks: List<Block>)

data class UsageInfo(
    val available: Boolean,
    val fiveHourPct: Int?, val fiveHourResets: String?,
    val sevenDayPct: Int?, val sevenDayResets: String?,
)

data class ContextInfo(val totalTokens: Long, val maxTokens: Long, val percentage: Int, val model: String)

data class SlashCommand(val name: String, val description: String, val argumentHint: String)

data class SessionSettings(val permissionMode: String, val model: String?, val effort: String?)

// Ответ /api/file: либо каталог со списком, либо файл с содержимым
data class DirChild(val name: String, val dir: Boolean)
data class FileEntry(
    val path: String,
    val isDir: Boolean,
    val entries: List<DirChild> = emptyList(),   // для каталога
    val size: Long = 0,                          // для файла
    val encoding: String = "utf8",               // utf8 | base64
    val content: String = "",                    // текст или base64
)

class ApiClient(private val baseUrl: String, private val token: String) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val sseHttp: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun authedRequest(url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer $token")

    private suspend fun get(path: String): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(authedRequest("$baseUrl$path").build()).execute().use { r ->
            val body = r.body?.string() ?: "{}"
            check(r.isSuccessful) { "HTTP ${r.code}: ${body.take(200)}" }
            json.parseToJsonElement(body).jsonObject
        }
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val rb = body.toString().toRequestBody("application/json".toMediaType())
        http.newCall(authedRequest("$baseUrl$path").post(rb).build()).execute().use { r ->
            val text = r.body?.string() ?: "{}"
            check(r.isSuccessful) { "HTTP ${r.code}: ${text.take(200)}" }
            json.parseToJsonElement(text).jsonObject
        }
    }

    fun streamUrl(afterSeq: Long) = "$baseUrl/api/stream?afterSeq=$afterSeq"

    suspend fun health(): Boolean = try { get("/api/health")["ok"]?.jsonPrimitive?.boolean == true } catch (_: Exception) { false }

    suspend fun sessions(): List<SessionInfo> =
        get("/api/sessions")["sessions"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            SessionInfo(
                id = o["id"]!!.jsonPrimitive.content,
                title = o["title"]?.jsonPrimitive?.contentOrNull ?: "(без названия)",
                lastText = o["lastText"]?.jsonPrimitive?.contentOrNull ?: "",
                mtime = o["mtime"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0L,
                messageCount = o["messageCount"]?.jsonPrimitive?.intOrNull ?: 0,
                running = o["running"]?.jsonPrimitive?.boolean ?: false,
                queued = o["queued"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }

    suspend fun history(sessionId: String): Pair<String, List<HistoryItem>> {
        val o = get("/api/sessions/$sessionId/history")
        val realId = o["sessionId"]!!.jsonPrimitive.content
        val items = o["items"]!!.jsonArray.map { el ->
            val it = el.jsonObject
            HistoryItem(
                uuid = it["uuid"]?.jsonPrimitive?.contentOrNull ?: "",
                ts = it["ts"]?.jsonPrimitive?.longOrNull ?: 0L,
                role = it["role"]!!.jsonPrimitive.content,
                blocks = parseBlocks(it["blocks"]!!.jsonArray),
            )
        }
        return realId to items
    }

    suspend fun send(sessionId: String?, text: String, attachments: List<JsonObject> = emptyList()): Pair<Long, String> {
        val o = post("/api/messages", buildJsonObject {
            if (sessionId != null) put("sessionId", sessionId)
            put("text", text)
            putJsonArray("attachments") { attachments.forEach { add(it) } }
        })
        return (o["jobId"]!!.jsonPrimitive.longOrNull ?: 0L) to o["sessionKey"]!!.jsonPrimitive.content
    }

    suspend fun interrupt(sessionId: String) { post("/api/sessions/$sessionId/interrupt", buildJsonObject {}) }

    suspend fun usage(): UsageInfo? {
        val u = get("/api/usage")["usage"] ?: return null
        if (u is kotlinx.serialization.json.JsonNull) return null
        val o = u.jsonObject
        fun pct(key: String) = (o[key] as? JsonObject)?.get("utilization")?.jsonPrimitive?.doubleOrNull?.toInt()
        fun resets(key: String) = (o[key] as? JsonObject)?.get("resets_at")?.jsonPrimitive?.contentOrNull
        return UsageInfo(
            available = o["available"]?.jsonPrimitive?.boolean ?: false,
            fiveHourPct = pct("fiveHour"), fiveHourResets = resets("fiveHour"),
            sevenDayPct = pct("sevenDay"), sevenDayResets = resets("sevenDay"),
        )
    }

    suspend fun context(sessionId: String): ContextInfo? {
        val c = get("/api/sessions/$sessionId/context")["context"] ?: return null
        if (c is kotlinx.serialization.json.JsonNull) return null
        val o = c.jsonObject
        return ContextInfo(
            totalTokens = o["totalTokens"]?.jsonPrimitive?.longOrNull ?: 0,
            maxTokens = o["maxTokens"]?.jsonPrimitive?.longOrNull ?: 1,
            percentage = o["percentage"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
            model = o["model"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    suspend fun commands(): List<SlashCommand> =
        get("/api/commands")["commands"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            SlashCommand(
                name = o["name"]!!.jsonPrimitive.content,
                description = o["description"]?.jsonPrimitive?.contentOrNull ?: "",
                argumentHint = o["argumentHint"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }

    suspend fun settings(sessionId: String): SessionSettings {
        val o = get("/api/sessions/$sessionId/settings")
        return SessionSettings(
            permissionMode = o["permissionMode"]?.jsonPrimitive?.contentOrNull ?: "bypassPermissions",
            model = o["model"]?.jsonPrimitive?.contentOrNull,
            effort = o["effort"]?.jsonPrimitive?.contentOrNull,
        )
    }

    suspend fun saveSettings(sessionId: String, permissionMode: String?, model: String?, effort: String?) {
        post("/api/sessions/$sessionId/settings", buildJsonObject {
            if (permissionMode != null) put("permissionMode", permissionMode)
            put("model", model)
            put("effort", effort)
        })
    }

    suspend fun upload(name: String, mime: String, base64: String): JsonObject =
        post("/api/upload", buildJsonObject {
            put("name", name); put("mime", mime); put("base64", base64)
        })["attachment"]!!.jsonObject

    suspend fun file(path: String): FileEntry {
        val o = get("/api/file?path=" + java.net.URLEncoder.encode(path, "UTF-8"))
        val isDir = o["dir"]?.jsonPrimitive?.boolean ?: false
        return FileEntry(
            path = o["path"]?.jsonPrimitive?.contentOrNull ?: path,
            isDir = isDir,
            entries = if (isDir) o["entries"]!!.jsonArray.map {
                val e = it.jsonObject
                DirChild(e["name"]!!.jsonPrimitive.content, e["dir"]?.jsonPrimitive?.boolean ?: false)
            } else emptyList(),
            size = o["size"]?.jsonPrimitive?.longOrNull ?: 0,
            encoding = o["encoding"]?.jsonPrimitive?.contentOrNull ?: "utf8",
            content = o["content"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }
}

fun parseBlocks(arr: kotlinx.serialization.json.JsonArray): List<Block> = arr.map { el ->
    val b = el.jsonObject
    Block(
        type = b["type"]!!.jsonPrimitive.content,
        text = b["text"]?.jsonPrimitive?.contentOrNull ?: "",
        id = b["id"]?.jsonPrimitive?.contentOrNull ?: "",
        name = b["name"]?.jsonPrimitive?.contentOrNull ?: "",
        input = b["input"]?.toString() ?: "",
        toolUseId = b["toolUseId"]?.jsonPrimitive?.contentOrNull ?: "",
        isError = b["isError"]?.jsonPrimitive?.boolean ?: false,
    )
}
