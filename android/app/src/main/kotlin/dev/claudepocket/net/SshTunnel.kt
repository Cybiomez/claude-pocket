package dev.claudepocket.net

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import dev.claudepocket.ConnectionPrefs
import java.io.ByteArrayOutputStream
import java.util.Properties

// SSH-сессия + локальный порт-форвардинг на демон (localhost сервера).
// Демон снаружи не виден — весь трафик внутри SSH.
class SshTunnel(private val prefs: ConnectionPrefs) {
    private var session: Session? = null
    var localPort: Int = -1
        private set
    var token: String = ""
        private set

    val isConnected: Boolean get() = session?.isConnected == true

    fun connect() {
        val jsch = JSch()
        if (prefs.authType == "key") {
            jsch.addIdentity(
                "mobile-key",
                prefs.privateKey.toByteArray(),
                null,
                prefs.keyPassphrase.ifBlank { null }?.toByteArray(),
            )
        }
        val s = jsch.getSession(prefs.user, prefs.host, prefs.port)
        if (prefs.authType == "password") s.setPassword(prefs.password)
        val cfg = Properties()
        cfg["StrictHostKeyChecking"] = "no" // TODO: доверять по first-use и пиннить отпечаток
        s.setConfig(cfg)
        s.timeout = 15000
        s.serverAliveInterval = 15000
        s.serverAliveCountMax = 4
        s.connect(15000)
        localPort = s.setPortForwardingL("127.0.0.1", 0, "127.0.0.1", prefs.daemonPort)
        session = s
        token = exec("cat ~/.claude-pocket/token").trim()
        check(token.isNotBlank()) { "Токен демона не найден — установлен ли claude-pocketd на сервере?" }
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
