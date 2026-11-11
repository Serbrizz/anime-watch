@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.example.animewatch.ui.components.LocalScreenBars
import com.example.animewatch.ui.components.ScreenBars
import com.example.animewatch.ui.components.UserAvatar
import com.example.animewatch.ui.detail.AnimeDetailScreen
import com.example.animewatch.ui.invites.InvitesScreen
import com.example.animewatch.ui.lists.ListDetailScreen
import com.example.animewatch.ui.lists.ListMembersScreen
import com.example.animewatch.ui.lists.ListsScreen
import com.example.animewatch.ui.login.LoginScreen
import com.example.animewatch.ui.profile.ProfileScreen
import com.example.animewatch.ui.recs.RecommendationsScreen
import com.example.animewatch.ui.recs.RecommendationsViewModel
import com.example.animewatch.ui.search.SearchScreen
import com.example.animewatch.ui.search.SearchViewModel
import com.example.animewatch.ui.update.UpdateDialog
import com.example.animewatch.ui.update.UpdateViewModel
import com.example.animewatch.ui.update.UpdatesScreen

private object Routes {
    const val LISTS = "lists"
    const val SEARCH = "search"
    const val RECS = "recs"
    const val LIST_DETAIL = "list/{listId}"
    const val LIST_MEMBERS = "list/{listId}/members"
    // La ricerca aperta da una lista: ciò che si aggiunge va dritto in quella lista.
    const val LIST_SEARCH = "list/{listId}/search"
    const val ANIME_DETAIL = "anime/{animeId}"
    // Il Profilo e le sue pagine hanno tutte il prefisso "profile": lì il pulsante del profilo non compare.
    const val PROFILE = "profile"
    const val INVITES = "profile/invites"
    const val UPDATES = "profile/updates"
    fun list(id: String) = "list/$id"
    fun listMembers(id: String) = "list/$id/members"
    fun listSearch(id: String) = "list/$id/search"
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
    val authState by appVm.authState.collectAsStateWithLifecycle()
    val updateVm: UpdateViewModel = viewModel(factory = viewModelFactory {
        initializer { UpdateViewModel(container.updater) }
    })
    val update by updateVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Anche al ritorno dalle impostazioni "installa app sconosciute", dove può essere in attesa un aggiornamento.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updateVm.onResume() }
    LaunchedEffect(updateVm) {
        updateVm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    when (authState) {
        // Sfondo vuoto per un istante, così all'avvio non lampeggia il login se la sessione c'è già.
        AuthState.Loading -> Surface(Modifier.fillMaxSize()) {}
        AuthState.SignedOut -> LoginScreen(appVm)
        // La navigazione vive qui dentro: all'uscita viene scartata e il prossimo accesso riparte da Liste.
        is AuthState.SignedIn -> MainContent(container, appVm, updateVm)
    }

    if (update.dialogOpen) {
        UpdateDialog(update, updateVm.currentVersion, onInstall = updateVm::install, onDismiss = updateVm::dismissDialog)
    }
}

@Composable
private fun MainContent(container: AppContainer, appVm: AppViewModel, updateVm: UpdateViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val error by appVm.error.collectAsStateWithLifecycle()
    val bars = remember { ScreenBars() }

    // Una barra in alto per tutte le schermate: ognuna dichiara solo titolo e azioni con ScreenBar.
    CompositionLocalProvider(LocalScreenBars provides bars) {
        Scaffold(
            topBar = {
                val spec = bars[backStack]
                val section = topLevel.firstOrNull { it.route == destination?.route }
                TopAppBar(
                    title = {
                        // Una sezione della barra in basso che non dichiara ScreenBar prende la propria etichetta.
                        Text(spec?.title ?: section?.label.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        if (backStack != null && section == null) {
                            IconButton(onClick = { nav.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                            }
                        }
                    },
                    actions = {
                        spec?.actions?.invoke(this)
                        // Dentro il Profilo (e nelle sue pagine) il pulsante riporterebbe solo lì.
                        if (destination?.route?.startsWith(Routes.PROFILE) != true) {
                            ProfileButton(appVm, updateVm, onClick = {
                                nav.navigate(Routes.PROFILE) { launchSingleTop = true }
                            })
                        }
                    },
                )
            },
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
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = appVm::dismissError) { Text("Chiudi") }
                        }
                    }
                }

                NavHost(nav, startDestination = Routes.LISTS, modifier = Modifier.weight(1f)) {
                    composable(Routes.LISTS) {
                        ListsScreen(appVm, onOpenList = { nav.navigate(Routes.list(it)) })
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            appVm,
                            updateVm,
                            onOpenInvites = { nav.navigate(Routes.INVITES) },
                            onOpenUpdates = { nav.navigate(Routes.UPDATES) },
                        )
                    }
                    composable(Routes.INVITES) { InvitesScreen(appVm) }
                    composable(Routes.UPDATES) { UpdatesScreen(updateVm) }
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
                        val listId = entry.arguments?.getString("listId").orEmpty()
                        ListDetailScreen(
                            vm = appVm,
                            listId = listId,
                            onOpenAnime = { nav.navigate(Routes.anime(it)) },
                            onOpenMembers = { nav.navigate(Routes.listMembers(listId)) },
                            onAddAnime = { nav.navigate(Routes.listSearch(listId)) },
                        )
                    }
                    composable(Routes.LIST_SEARCH) { entry ->
                        // Un SearchViewModel per questa voce del back stack: la ricerca della scheda Cerca resta com'era.
                        val searchVm: SearchViewModel = viewModel(factory = viewModelFactory {
                            initializer { SearchViewModel(container.animeRepo) }
                        })
                        SearchScreen(
                            appVm,
                            searchVm,
                            onOpenAnime = { nav.navigate(Routes.anime(it)) },
                            targetListId = entry.arguments?.getString("listId").orEmpty(),
                        )
                    }
                    composable(Routes.LIST_MEMBERS) { entry ->
                        ListMembersScreen(
                            vm = appVm,
                            listId = entry.arguments?.getString("listId").orEmpty(),
                            // Dopo "Esci dalla lista" si torna alle liste, non al dettaglio di una lista non più nostra.
                            onLeft = { nav.popBackStack(Routes.LISTS, inclusive = false) },
                        )
                    }
                    composable(
                        Routes.ANIME_DETAIL,
                        arguments = listOf(navArgument("animeId") { type = NavType.IntType }),
                    ) { entry ->
                        AnimeDetailScreen(vm = appVm, animeId = entry.arguments?.getInt("animeId") ?: 0)
                    }
                }
            }
        }
    }
}

/** Foto del profilo in alto a destra: il numero di inviti in attesa, oppure un pallino se c'è un aggiornamento. */
@Composable
private fun ProfileButton(appVm: AppViewModel, updateVm: UpdateViewModel, onClick: () -> Unit) {
    val me by appVm.user.collectAsStateWithLifecycle()
    val invites by appVm.incomingInvites.collectAsStateWithLifecycle()
    val update by updateVm.state.collectAsStateWithLifecycle()
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "Profilo" }) {
        BadgedBox(
            badge = {
                when {
                    invites.isNotEmpty() -> Badge { Text("${invites.size}") }
                    update.available != null -> Badge()
                }
            },
        ) {
            UserAvatar(me?.photoUrl, size = 32.dp)
        }
    }
}
