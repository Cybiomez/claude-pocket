package dev.claudepocket.net

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.claudepocket.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

// Проверка обновлений по GitHub Releases (публичный API, без токена).
// Не чаще раза в сутки; скачивание APK и запуск системного установщика.
data class UpdateInfo(val version: String, val apkUrl: String, val releaseUrl: String, val notes: String)

object UpdateChecker {
    private const val REPO = "Cybiomez/claude-pocket"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun checkIfDue(ctx: Context, force: Boolean = false): UpdateInfo? {
        val p = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val last = p.getLong("lastCheck", 0)
        if (!force && System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return null
        p.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        return check()
    }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "claude-pocket/${BuildConfig.VERSION_NAME}")
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val o = json.parseToJsonElement(r.body!!.string()).jsonObject
                val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
                val version = tag.removePrefix("v")
                if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
                val apk = o["assets"]?.jsonArray
                    ?.map { it.jsonObject }
                    ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
                    ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull ?: return@withContext null
                UpdateInfo(
                    version = version,
                    apkUrl = apk,
                    releaseUrl = o["html_url"]?.jsonPrimitive?.contentOrNull ?: "",
                    notes = (o["body"]?.jsonPrimitive?.contentOrNull ?: "").take(500),
                )
            }
        } catch (_: Exception) { null }
    }

    // Сравнение semver: 0.3.0 новее 0.2.1. Dev-сборки (0.0.0-dev) обновляются всегда.
    fun isNewer(candidate: String, current: String): Boolean {
        if (current.endsWith("-dev")) return true
        fun parts(v: String) = v.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() } + listOf(0, 0, 0)
        val a = parts(candidate); val b = parts(current)
        for (i in 0..2) {
            if (a[i] != b[i]) return a[i] > b[i]
        }
        return false
    }

    // Качаем APK в кэш и открываем системный установщик (подпись та же — встанет поверх)
    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "claude-pocket-${info.version}.apk")
            val req = Request.Builder().url(info.apkUrl)
                .header("User-Agent", "claude-pocket/${BuildConfig.VERSION_NAME}").build()
            http.newCall(req).execute().use { r ->
                check(r.isSuccessful) { "HTTP ${r.code}" }
                val total = r.body!!.contentLength()
                r.body!!.byteStream().use { input ->
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done * 100 / total).toInt())
                        }
                    }
                }
            }
            out
        }
        val uri = FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
