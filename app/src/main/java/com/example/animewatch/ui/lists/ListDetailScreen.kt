package com.example.animewatch.ui.lists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.Franchise
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AnimeRow
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.SkeletonAnimeRow
import com.example.animewatch.ui.components.ScreenBar

/**
 * Una serie nella lista: copertina e titolo della prima stagione, avanzamento della stagione in corso.
 * Il tocco apre quella stagione, dove il selettore porta alle altre.
 */
@Composable
private fun FranchiseRow(
    seasons: List<Anime>,
    watchedCount: (Int) -> Int,
    onOpen: (Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val current = seasons[Franchise.currentSeasonIndex(seasons, watchedCount)]
    val watched = watchedCount(current.id)
    val total = current.availableEpisodes
    val seen = seasons.count { it.isSeasonComplete(watchedCount(it.id)) }
    val allSeen = seen == seasons.size
    val episodes = if (total > 0) "$watched/$total puntate viste" else "Puntate non ancora disponibili"

    AnimeRow(
        anime = seasons.first(),
        onClick = { onOpen(current.id) },
        trailing = {
            if (allSeen) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (seasons.size > 1) "Tutte le stagioni viste" else "Stagione vista",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Rimuovi dalla lista")
                }
            }
        },
    ) {
        if (seasons.size > 1) {
            val label = Franchise.seasonLabels(seasons)[seasons.indexOf(current)]
            Text(
                "$seen di ${seasons.size} stagioni viste",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$label · $episodes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(episodes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (total > 0) {
            LinearProgressIndicator(
                progress = { (watched.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ListDetailScreen(
    vm: AppViewModel,
    listId: String,
    onOpenAnime: (Int) -> Unit,
    onOpenMembers: () -> Unit,
    onAddAnime: () -> Unit,
) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val animeMap by vm.anime.collectAsStateWithLifecycle()
    val me by vm.user.collectAsStateWithLifecycle()
    val list = lists.firstOrNull { it.id == listId }
    val canEdit = list?.canEdit(me?.uid) == true

    val franchises by vm.franchises.collectAsStateWithLifecycle()
    val groups = remember(list?.animeIds, franchises) { Franchise.groupList(list?.animeIds.orEmpty(), franchises) }

    LaunchedEffect(list?.animeIds) { list?.let { vm.ensureFranchises(it.animeIds) } }

    ScreenBar(list?.name ?: "Lista") {
        if (list != null) {
            IconButton(onClick = onOpenMembers) {
                Icon(Icons.Default.Share, contentDescription = "Condivisione e membri")
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = onAddAnime,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Aggiungi anime") },
                )
            }
        },
    ) { padding ->
        when {
            // Succede anche quando il proprietario la elimina o ci rimuove mentre siamo qui.
            list == null -> EmptyState("Lista non trovata.", Modifier.padding(padding))
            list.animeIds.isEmpty() -> EmptyState(
                if (canEdit) "Lista vuota. Tocca \"Aggiungi anime\" per cercarne uno e metterlo qui."
                else "Lista vuota. Puoi solo visualizzarla: gli anime li aggiunge chi ha il permesso di modifica.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                // In fondo resta spazio per il pulsante "Aggiungi anime", che altrimenti coprirebbe l'ultima riga.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            ) {
                if (!canEdit) {
                    item(key = "viewer") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) {
                            Text(
                                "Sola visualizzazione: puoi segnare le tue puntate, ma non modificare la lista.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                items(groups, key = { it.rootId }) { franchise ->
                    val seasons = franchise.seasonIds.mapNotNull { animeMap[it] }
                    if (seasons.isEmpty()) {
                        SkeletonAnimeRow()
                    } else {
                        FranchiseRow(
                            seasons = seasons,
                            watchedCount = { progress[it]?.watchedEpisodes?.size ?: 0 },
                            onOpen = onOpenAnime,
                            onRemove = if (canEdit) ({ vm.removeFromList(list, franchise) }) else null,
                        )
                    }
                }
            }
        }
    }
}
