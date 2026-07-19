package dev.claudepocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

// Приёмник статусов установки обновления через PackageInstaller.
// Первое самообновление система подтверждает диалогом (STATUS_PENDING_USER_ACTION),
// после него приложение становится «установщиком записи» и на Android 12+
// следующие обновления проходят без подтверждений.
class UpdateReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "dev.claudepocket.UPDATE_STATUS"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm =
                    if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else
                        @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Процесс будет перезапущен системой с новой версией
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(
                    ctx,
                    "Обновление не установлено: ${msg ?: "неизвестная ошибка"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
