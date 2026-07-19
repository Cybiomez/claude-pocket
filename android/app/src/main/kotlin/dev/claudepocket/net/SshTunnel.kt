package dev.claudepocket.net

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import dev.claudepocket.ConnectionPrefs
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

// SSH-сессия + локальный порт-форвардинг на демон (localhost сервера).
// Демон снаружи не виден — весь трафик внутри SSH.
//
// Отпечаток сервера: TOFU (trust on first use) — при первом подключении ключ хоста
// запоминается в knownHostsFile, дальше строго сверяется. Сменился ключ — ошибка.
//
// Ключ устройства: при первом успешном входе по паролю приложение генерирует свою
// пару ключей, дописывает публичный в ~/.ssh/authorized_keys сервера и дальше
// заходит по ключу (пароль остаётся запасным путём).
class SshTunnel(
    private val prefs: ConnectionPrefs,
    private val knownHostsFile: File,
    // false = разовое подключение: ключ устройства на сервер не прописываем
    private val enroll: Boolean = true,
) {
    private var session: Session? = null
    var localPort: Int = -1
        private set
    var token: String = ""
        private set

    // Заполняется, если в этом подключении был сгенерирован и прописан новый ключ устройства
    var newDeviceKey: String? = null
        private set

    val isConnected: Boolean get() = session?.isConnected == true

    fun connect() {
        val jsch = JSch()
        if (knownHostsFile.exists()) jsch.setKnownHosts(knownHostsFile.absolutePath)

        // Аутентификация: ключ устройства (если уже есть) → пользовательский ключ → пароль.
        // JSch сам перебирает publickey до password.
        if (prefs.deviceKey.isNotBlank()) {
            jsch.addIdentity("device-key", prefs.deviceKey.toByteArray(), null, null)
        }
        if (prefs.authType == "key" && prefs.privateKey.isNotBlank()) {
            jsch.addIdentity(
                "user-key",
                prefs.privateKey.toByteArray(),
                null,
                prefs.keyPassphrase.ifBlank { null }?.toByteArray(),
            )
        }

        val s = jsch.getSession(prefs.user, prefs.host, prefs.port)
        if (prefs.password.isNotBlank()) s.setPassword(prefs.password)

        val firstUse = !knownHostsFile.exists() || knownHostsFile.length() == 0L
        val cfg = Properties()
        // Первый раз — принимаем и запоминаем; дальше — строгая проверка по known_hosts.
        cfg["StrictHostKeyChecking"] = if (firstUse) "no" else "yes"
        // Сначала ключ (устройства или пользовательский), пароль — запасной путь
        cfg["PreferredAuthentications"] = "publickey,password,keyboard-interactive"
        s.setConfig(cfg)
        s.timeout = 15000
        s.serverAliveInterval = 15000
        s.serverAliveCountMax = 4
        try {
            s.connect(15000)
        } catch (e: com.jcraft.jsch.JSchException) {
            if (e.message?.contains("HostKey", ignoreCase = true) == true ||
                e.message?.contains("UnknownHostKey", ignoreCase = true) == true
            ) {
                throw IllegalStateException(
                    "Ключ сервера изменился! Возможна подмена сервера (MITM). " +
                        "Если сервер переустанавливали, поможет кнопка «забыть старый отпечаток» на экране подключения."
                )
            }
            throw e
        }
        if (firstUse) rememberHostKey(s.hostKey)

        localPort = s.setPortForwardingL("127.0.0.1", 0, "127.0.0.1", prefs.daemonPort)
        session = s

        token = exec("cat ~/.claude-pocket/token").trim()
        check(token.isNotBlank()) { "Токен демона не найден — установлен ли claude-pocketd на сервере?" }

        // Первый вход по паролю без ключа устройства → генерируем и прописываем
        if (enroll && prefs.deviceKey.isBlank() && prefs.password.isNotBlank()) {
            try { newDeviceKey = enrollDeviceKey(jsch) } catch (_: Exception) { /* не критично — остаёмся на пароле */ }
        }
    }

    private fun rememberHostKey(hk: HostKey?) {
        hk ?: return
        val hostPart = if (prefs.port != 22) "[${prefs.host}]:${prefs.port}" else prefs.host
        knownHostsFile.parentFile?.mkdirs()
        knownHostsFile.writeText("$hostPart ${hk.type} ${hk.key}\n")
    }

    fun forgetHostKey() {
        knownHostsFile.delete()
    }

    // Генерация пары на устройстве + добавление публичного ключа в authorized_keys.
    // Возвращает приватный ключ (OpenSSH/PEM-текст) для сохранения в настройках.
    private fun enrollDeviceKey(jsch: JSch): String {
        val kp = try {
            KeyPair.genKeyPair(jsch, KeyPair.ED25519, 0)
        } catch (_: Throwable) {
            KeyPair.genKeyPair(jsch, KeyPair.RSA, 3072)
        }
        val priv = ByteArrayOutputStream().also { kp.writePrivateKey(it) }.toString("UTF-8")
        val pub = ByteArrayOutputStream().also { kp.writePublicKey(it, "claude-pocket-${android.os.Build.MODEL.replace(' ', '-')}") }
            .toString("UTF-8").trim()
        kp.dispose()
        // Дописываем, только если такого ключа ещё нет; выставляем права
        val cmd = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && " +
            "grep -qF '${pub.substringAfter(' ').substringBefore(' ')}' ~/.ssh/authorized_keys || " +
            "printf '%s\\n' '$pub' >> ~/.ssh/authorized_keys; chmod 600 ~/.ssh/authorized_keys; echo enrolled"
        val out = exec(cmd)
        check(out.contains("enrolled")) { "Не удалось прописать ключ" }
        return priv
    }

    fun exec(command: String): String {
        val s = session ?: error("нет SSH-сессии")
        val ch = s.openChannel("exec") as com.jcraft.jsch.ChannelExec
        ch.setCommand(command)
        val out = ByteArrayOutputStream()
        ch.outputStream = null
        ch.setErrStream(ByteArrayOutputStream())
        val input = ch.inputStream
        ch.connect(10000)
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        ch.disconnect()
        return out.toString("UTF-8")
    }

    fun disconnect() {
        try { session?.disconnect() } catch (_: Exception) {}
        session = null
        localPort = -1
    }
}
