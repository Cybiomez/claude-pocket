package dev.claudepocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.claudepocket.AppViewModel
import dev.claudepocket.ConnState

@Composable
fun SetupScreen(vm: AppViewModel) {
    var host by remember { mutableStateOf(vm.prefs.host) }
    var port by remember { mutableStateOf(vm.prefs.port.toString()) }
    var user by remember { mutableStateOf(vm.prefs.user) }
    var authType by remember { mutableStateOf(vm.prefs.authType) }
    var password by remember { mutableStateOf(vm.prefs.password) }
    var privateKey by remember { mutableStateOf(vm.prefs.privateKey) }
    var passphrase by remember { mutableStateOf(vm.prefs.keyPassphrase) }

    val conn = vm.conn

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Claude Pocket", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Подключение к своему серверу с Claude Code по SSH",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it }, label = { Text("Адрес сервера") }, modifier = Modifier.weight(2f), singleLine = true)
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Порт") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp), singleLine = true)
        }
        OutlinedTextField(user, { user = it }, label = { Text("Пользователь") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = authType == "password", onClick = { authType = "password" }, label = { Text("Пароль") })
            FilterChip(selected = authType == "key", onClick = { authType = "key" }, label = { Text("SSH-ключ") })
        }

        if (authType == "password") {
            OutlinedTextField(
                password, { password = it }, label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        } else {
            OutlinedTextField(
                privateKey, { privateKey = it },
                label = { Text("Приватный ключ (вставь текст целиком)") },
                placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----…") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            OutlinedTextField(
                passphrase, { passphrase = it }, label = { Text("Пароль ключа (если есть)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }

        if (conn is ConnState.Failed) {
            Text("Ошибка: ${conn.error}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            if (conn.error.contains("Ключ сервера изменился")) {
                Button(onClick = { vm.forgetHostKey() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Я уверен, что это мой сервер — забыть старый отпечаток")
                }
            }
        }

        Button(
            onClick = {
                vm.savePrefs(
                    vm.prefs.copy(
                        host = host.trim(), port = port.toIntOrNull() ?: 22, user = user.trim(),
                        authType = authType, password = password, privateKey = privateKey, keyPassphrase = passphrase,
                    )
                )
                vm.connect()
            },
            enabled = conn !is ConnState.Connecting && host.isNotBlank() && user.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (conn is ConnState.Connecting) {
                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
                Text(conn.step)
            } else Text("Подключиться")
        }
    }
}
