package dev.claudepocket

import android.content.Context
import kotlinx.serialization.Serializable

// Параметры одного подключения. Хранятся только на устройстве (см. ConnectionStore);
// пароль и ключи дальше устройства не уходят.
@Serializable
data class ConnectionPrefs(
    val id: String = "",               // пустой = ещё не сохранено в список
    val name: String = "",             // удобное имя; пустое = показываем user@host
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val authType: String = "password", // password | key
    val password: String = "",
    val privateKey: String = "",
    val keyPassphrase: String = "",
    val daemonPort: Int = 8787,
    // Ключ, сгенерированный самим приложением при первом входе по паролю
    // и прописанный в authorized_keys сервера. Дальше вход идёт по нему.
    val deviceKey: String = "",
) {
    val displayName: String
        get() = name.ifBlank { "$user@$host" + if (port != 22) ":$port" else "" }
}

// Старое хранилище одного подключения (SharedPreferences, открытым текстом).
// Оставлено только ради миграции в ConnectionStore; после неё файл удаляется.
object LegacyPrefs {
    private const val NAME = "conn"

    fun loadAndClear(ctx: Context): ConnectionPrefs? {
        val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val host = p.getString("host", "") ?: ""
        val user = p.getString("user", "") ?: ""
        if (host.isBlank() || user.isBlank()) return null
        val c = ConnectionPrefs(
            host = host,
            port = p.getInt("port", 22),
            user = user,
            authType = p.getString("authType", "password") ?: "password",
            password = p.getString("password", "") ?: "",
            privateKey = p.getString("privateKey", "") ?: "",
            keyPassphrase = p.getString("keyPassphrase", "") ?: "",
            daemonPort = p.getInt("daemonPort", 8787),
            deviceKey = p.getString("deviceKey", "") ?: "",
        )
        p.edit().clear().apply()
        return c
    }
}
