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
    const val REPO = "Cybiomez/claude-pocket"
    const val REPO_URL = "https://github.com/$REPO"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    // Канал обновлений: latest — только стабильные релизы, dev — плюс pre-release из dev-ветки
    const val CHANNEL_LATEST = "latest"
    const val CHANNEL_DEV = "dev"

    fun channel(ctx: Context): String =
        ctx.getSharedPreferences("updates", Context.MODE_PRIVATE).getString("channel", CHANNEL_LATEST) ?: CHANNEL_LATEST

    fun setChannel(ctx: Context, value: String) {
        ctx.getSharedPreferences("updates", Context.MODE_PRIVATE).edit()
            .putString("channel", value).putLong("lastCheck", 0).apply() // сброс интервала — сразу перепроверить
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun checkIfDue(ctx: Context, force: Boolean = false): UpdateInfo? {
        val p = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val last = p.getLong("lastCheck", 0)
        if (!force && System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return null
        p.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        return try { check(channel(ctx)) } catch (_: Exception) { null }
    }

    // null = обновления нет; сетевые/прочие ошибки пробрасываются наружу.
    // latest: /releases/latest (pre-release пропускается GitHub автоматически).
    // dev: /releases (первый в списке, включая pre-release).
    suspend fun check(channel: String = CHANNEL_LATEST): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = if (channel == CHANNEL_DEV)
            "https://api.github.com/repos/$REPO/releases?per_page=10"
        else
            "https://api.github.com/repos/$REPO/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "claude-pocket/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("GitHub ответил HTTP ${r.code}")
            val el = json.parseToJsonElement(r.body!!.string())
            // dev — берём самый свежий релиз (не draft), latest — единственный объект
            val o = if (channel == CHANNEL_DEV)
                el.jsonArray.map { it.jsonObject }.firstOrNull { it["draft"]?.jsonPrimitive?.contentOrNull != "true" }
                    ?: return@withContext null
            else el.jsonObject
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
    }

    // Сравнение semver: 0.3.0 новее 0.2.1. Dev-сборки (0.0.0-dev) обновляются всегда.
    fun isNewer(candidate: String, current: String): Boolean {
        // Локальная сборка без тега (0.0.0-dev) — обновляем на что угодно
        if (current == "0.0.0-dev") return true
        fun base(v: String) = v.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() } + listOf(0, 0, 0)
        val a = base(candidate); val b = base(current)
        for (i in 0..2) {
            if (a[i] != b[i]) return a[i] > b[i]
        }
        // Базовые номера равны — сравниваем pre-release-часть (semver):
        // отсутствие суффикса = финальный релиз, он новее любой pre-release того же номера
        // (0.4.5 новее 0.4.5-dev.2), а между pre-release'ами сравниваем суффиксы (dev.2 > dev.1).
        val aPre = candidate.substringAfter('-', "")
        val bPre = current.substringAfter('-', "")
        if (aPre == bPre) return false
        if (aPre.isEmpty()) return true    // кандидат финальный, текущий pre-release
        if (bPre.isEmpty()) return false   // кандидат pre-release, текущий финальный
        return comparePre(aPre, bPre) > 0
    }

    // Сравнение pre-release-идентификаторов через точку: числа — численно, иначе — лексически.
    private fun comparePre(a: String, b: String): Int {
        val aa = a.split('.'); val bb = b.split('.')
        for (i in 0 until maxOf(aa.size, bb.size)) {
            val x = aa.getOrNull(i) ?: return -1
            val y = bb.getOrNull(i) ?: return 1
            val xn = x.toIntOrNull(); val yn = y.toIntOrNull()
            val c = if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
            if (c != 0) return c
        }
        return 0
    }

    // Качаем APK в кэш (с проверкой, что файл докачался целиком) и устанавливаем
    // Только скачивание APK (с проверкой целостности). Возвращает готовый файл;
    // установку запускает отдельно install() — чтобы кнопку можно было нажать
    // повторно, если системный установщик замял первую попытку.
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
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
                if (total > 0 && out.length() != total) {
                    throw IllegalStateException(
                        "файл скачался не полностью (${out.length()} из $total байт), попробуйте ещё раз"
                    )
                }
            }
            out
        }

    // Запуск установки уже скачанного APK. Сначала самообновление через
    // PackageInstaller, при ошибке — системный установщик через ACTION_VIEW.
    fun install(ctx: Context, file: File) {
        try {
            installSelf(ctx, file)
        } catch (_: Exception) {
            legacyInstall(ctx, file)
        }
    }

    // Самообновление через PackageInstaller: без ACTION_VIEW и «чужого» APK —
    // меньше поводов для Play Protect. Первое обновление система подтверждает
    // диалогом, дальше на Android 12+ ставится без вопросов (USER_ACTION_NOT_REQUIRED).
    private fun installSelf(ctx: Context, file: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(
            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(BuildConfig.APPLICATION_ID)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(
                    android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                )
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { s ->
            s.openWrite("update.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val statusIntent = Intent(dev.claudepocket.UpdateReceiver.ACTION)
                .setPackage(ctx.packageName)
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0)
            val pending = android.app.PendingIntent.getBroadcast(ctx, sessionId, statusIntent, flags)
            s.commit(pending.intentSender)
        }
    }

    // Запасной путь — системный установщик через ACTION_VIEW (как было раньше)
    private fun legacyInstall(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
