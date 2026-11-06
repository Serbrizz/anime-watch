package com.example.animewatch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.animewatch.di.AppContainer
import com.example.animewatch.ui.detail.AnimeDetailScreen
import com.example.animewatch.ui.lists.ListDetailScreen
import com.example.animewatch.ui.lists.ListsScreen
import com.example.animewatch.ui.recs.RecommendationsScreen
import com.example.animewatch.ui.recs.RecommendationsViewModel
import com.example.animewatch.ui.search.SearchScreen
import com.example.animewatch.ui.search.SearchViewModel

private object Routes {
    const val LISTS = "lists"
    const val SEARCH = "search"
    const val RECS = "recs"
    const val LIST_DETAIL = "list/{listId}"
    const val ANIME_DETAIL = "anime/{animeId}"
    fun list(id: String) = "list/$id"
    fun anime(id: Int) = "anime/$id"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.LISTS, "Liste", Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.SEARCH, "Cerca", Icons.Default.Search),
    TopLevel(Routes.RECS, "Consigliati", Icons.Default.Star),
)

@Composable
fun AnimeWatchRoot(container: AppContainer) {
    // Una sola istanza per tutta l'Activity: liste e progressi sono condivisi fra le schermate.
    val appVm: AppViewModel = viewModel(factory = viewModelFactory {
        initializer { AppViewModel(container) }
    })
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val error by appVm.error.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (topLevel.any { it.route == destination?.route }) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            error?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = appVm::signIn) { Text("Riprova") }
                        TextButton(onClick = appVm::dismissError) { Text("Chiudi") }
                    }
                }
            }

            NavHost(nav, startDestination = Routes.LISTS, modifier = Modifier.weight(1f)) {
                composable(Routes.LISTS) {
                    ListsScreen(appVm, onOpenList = { nav.navigate(Routes.list(it)) })
                }
                composable(Routes.SEARCH) {
                    val searchVm: SearchViewModel = viewModel(factory = viewModelFactory {
                        initializer { SearchViewModel(container.animeRepo) }
                    })
                    SearchScreen(appVm, searchVm, onOpenAnime = { nav.navigate(Routes.anime(it)) })
                }
                composable(Routes.RECS) {
                    val recsVm: RecommendationsViewModel = viewModel(factory = viewModelFactory {
                        initializer { RecommendationsViewModel(container.recommender) }
                    })
                    RecommendationsScreen(appVm, recsVm, onOpenAnime = { nav.navigate(Routes.anime(it)) })
                }
                composable(Routes.LIST_DETAIL) { entry ->
                    ListDetailScreen(
                        vm = appVm,
                        listId = entry.arguments?.getString("listId").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenAnime = { nav.navigate(Routes.anime(it)) },
                    )
                }
                composable(
                    Routes.ANIME_DETAIL,
                    arguments = listOf(navArgument("animeId") { type = NavType.IntType }),
                ) { entry ->
                    AnimeDetailScreen(
                        vm = appVm,
                        animeId = entry.arguments?.getInt("animeId") ?: 0,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
