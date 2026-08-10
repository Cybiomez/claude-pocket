package dev.claudepocket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.claudepocket.AppViewModel
import dev.claudepocket.ConnState
import kotlinx.coroutines.launch

// Навигация между страницами пейджера (список сессий ↔ чат)
class PagerNav(val toList: () -> Unit, val toChat: () -> Unit)

val LocalPagerNav = compositionLocalOf { PagerNav({}, {}) }

@Composable
fun AppRoot(vm: AppViewModel) {
    when {
        vm.conn !is ConnState.Connected -> SetupScreen(vm)
        vm.fileBrowserOpen -> FileBrowserScreen(vm)
        else -> {
            val scope = rememberCoroutineScope()
            // 2 страницы: 0 — список сессий, 1 — чат. Свайпать можно, когда чат открыт.
            val pager = rememberPagerState(pageCount = { 2 })
            val nav = remember(pager) {
                PagerNav(
                    toList = { scope.launch { pager.animateScrollToPage(0) } },
                    toChat = { scope.launch { pager.animateScrollToPage(1) } },
                )
            }
            // Чат закрыли (нет активной вкладки) — вернуться к списку
            LaunchedEffect(vm.activeTab) {
                if (vm.activeTab == null) pager.scrollToPage(0)
            }
            // Обновляем список при возврате на страницу списка (свежие имена/порядок)
            LaunchedEffect(pager.currentPage) {
                if (pager.currentPage == 0) { vm.refreshSessions(); vm.refreshUsage() }
            }
            // «Назад» на странице чата — к списку (не выходит из приложения)
            BackHandler(enabled = pager.currentPage == 1) { nav.toList() }

            CompositionLocalProvider(LocalPagerNav provides nav) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = vm.activeTab != null,
                    beyondViewportPageCount = 1,
                ) { page ->
                    if (page == 0) SessionsScreen(vm) else ChatScreen(vm)
                }
            }
        }
    }
}
