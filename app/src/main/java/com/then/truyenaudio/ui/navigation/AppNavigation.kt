package com.then.truyenaudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.then.truyenaudio.data.local.SavedLinkStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.then.truyenaudio.ui.detail.NovelDetailScreen
import com.then.truyenaudio.ui.home.HomeScreen
import com.then.truyenaudio.ui.home.HomeViewModel
import com.then.truyenaudio.ui.reader.ReaderScreen
import com.then.truyenaudio.ui.settings.SettingsScreen
import com.then.truyenaudio.ui.library.SavedLinksScreen

@Composable
fun AppNavigation(homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val state by homeViewModel.uiState.collectAsState()
    var savedLinks by remember { mutableStateOf(SavedLinkStore.load(context)) }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                state = state,
                onUrlChange = homeViewModel::setUrl,
                onLoad = {
                    savedLinks = SavedLinkStore.save(context, state.url)
                    homeViewModel.loadNovel()
                },
                onOpenNovel = { navController.navigate("detail") },
                onOpenSettings = { navController.navigate("settings") },
                savedLinkCount = savedLinks.size,
                onOpenSavedLinks = { navController.navigate("saved-links") }
            )
        }
        composable("detail") {
            NovelDetailScreen(
                novel = state.novel,
                chapters = state.chapters,
                isLoading = state.isLoading,
                error = state.error,
                onBack = { navController.popBackStack() },
                onChapterClick = { chapter ->
                    homeViewModel.loadChapter(chapter) {
                        navController.navigate("reader")
                    }
                }
            )
        }
        composable("reader") {
            ReaderScreen(
                chapter = state.selectedChapter,
                chapters = state.chapters,
                onBack = { navController.popBackStack() },
                onChapterChange = { chapter ->
                    homeViewModel.loadChapter(chapter) {}
                }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("saved-links") {
            SavedLinksScreen(
                links = savedLinks,
                onBack = { navController.popBackStack() },
                onLinkClick = { url ->
                    homeViewModel.setUrl(url)
                    homeViewModel.loadNovel()
                    navController.popBackStack()
                },
                onDelete = { url ->
                    savedLinks = SavedLinkStore.delete(context, url)
                }
            )
        }
    }
}
