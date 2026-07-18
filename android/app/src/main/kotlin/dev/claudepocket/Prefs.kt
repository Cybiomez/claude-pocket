package dev.claudepocket

import android.content.Context

// Настройки подключения. Приватное app-хранилище; ключ и пароль дальше устройства не уходят.
data class ConnectionPrefs(
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
    val isFilled: Boolean
        get() = host.isNotBlank() && user.isNotBlank() &&
            (if (authType == "password") password.isNotBlank() else privateKey.isNotBlank())
}

object Prefs {
    private const val NAME = "conn"

    fun load(ctx: Context): ConnectionPrefs {
        val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return ConnectionPrefs(
            host = p.getString("host", "") ?: "",
            port = p.getInt("port", 22),
            user = p.getString("user", "") ?: "",
            authType = p.getString("authType", "password") ?: "password",
            password = p.getString("password", "") ?: "",
            privateKey = p.getString("privateKey", "") ?: "",
            keyPassphrase = p.getString("keyPassphrase", "") ?: "",
            daemonPort = p.getInt("daemonPort", 8787),
            deviceKey = p.getString("deviceKey", "") ?: "",
        )
    }

    fun save(ctx: Context, c: ConnectionPrefs) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("host", c.host)
            .putInt("port", c.port)
            .putString("user", c.user)
            .putString("authType", c.authType)
            .putString("password", c.password)
            .putString("privateKey", c.privateKey)
            .putString("keyPassphrase", c.keyPassphrase)
            .putInt("daemonPort", c.daemonPort)
            .putString("deviceKey", c.deviceKey)
            .apply()
    }
}
