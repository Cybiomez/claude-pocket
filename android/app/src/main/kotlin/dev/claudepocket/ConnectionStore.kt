package dev.claudepocket

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Список сохранённых подключений: JSON, зашифрованный AES-GCM.
// Ключ шифрования живёт в Android Keystore (аппаратное хранилище) и не покидает
// устройство — поэтому файл бесполезен вне этого телефона, даже если утечёт
// (бэкап и так запрещён в манифесте, это второй слой).
// Порядок элементов списка = порядок карточек на экране.
object ConnectionStore {
    private const val FILE = "connections.enc"
    private const val KEY_ALIAS = "connections-store"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(ctx: Context): List<ConnectionPrefs> {
        migrate(ctx)
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            json.decodeFromString<List<ConnectionPrefs>>(decrypt(f.readBytes()).decodeToString())
        } catch (_: Exception) {
            // Расшифровка не удалась (например, ключ в Keystore потерян) —
            // восстановить нечем, начинаем с пустого списка.
            f.delete()
            emptyList()
        }
    }

    fun save(ctx: Context, list: List<ConnectionPrefs>) {
        val f = File(ctx.filesDir, FILE)
        f.writeBytes(encrypt(json.encodeToString(list).encodeToByteArray()))
    }

    fun newId(): String = UUID.randomUUID().toString()

    // Единственное подключение из старого открытого хранилища переносим первым элементом
    private fun migrate(ctx: Context) {
        if (File(ctx.filesDir, FILE).exists()) return
        val old = LegacyPrefs.loadAndClear(ctx) ?: return
        save(ctx, listOf(old.copy(id = newId())))
    }

    // --- шифрование ---

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    // Формат файла: [12 байт IV][шифротекст+тег GCM]
    private fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }
}
