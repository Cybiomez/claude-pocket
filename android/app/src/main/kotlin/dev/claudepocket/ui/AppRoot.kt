package dev.claudepocket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import dev.claudepocket.AppViewModel
import dev.claudepocket.ConnState

@Composable
fun AppRoot(vm: AppViewModel) {
    when {
        vm.conn !is ConnState.Connected -> SetupScreen(vm)
        vm.fileBrowserOpen -> FileBrowserScreen(vm)
        vm.activeTab != null -> {
            BackHandler { vm.activeTab = null }
            ChatScreen(vm)
        }
        else -> SessionsScreen(vm)
    }
}
