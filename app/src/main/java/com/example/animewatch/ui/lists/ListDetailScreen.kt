@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.lists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AnimeRow
import com.example.animewatch.ui.components.EmptyState

@Composable
fun ListDetailScreen(
    vm: AppViewModel,
    listId: String,
    onBack: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    onOpenMembers: () -> Unit,
) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val animeMap by vm.anime.collectAsStateWithLifecycle()
    val me by vm.user.collectAsStateWithLifecycle()
    val list = lists.firstOrNull { it.id == listId }
    val canEdit = list?.canEdit(me?.uid) == true

    LaunchedEffect(list?.animeIds) { list?.let { vm.ensureAnime(it.animeIds) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list?.name ?: "Lista") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (list != null) {
                        IconButton(onClick = onOpenMembers) {
                            Icon(Icons.Default.Share, contentDescription = "Condivisione e membri")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            // Succede anche quando il proprietario la elimina o ci rimuove mentre siamo qui.
            list == null -> EmptyState("Lista non trovata.", Modifier.padding(padding))
            list.animeIds.isEmpty() -> EmptyState(
                if (canEdit) "Lista vuota. Cerca un anime e aggiungilo con il pulsante +."
                else "Lista vuota. Puoi solo visualizzarla: gli anime li aggiunge chi ha il permesso di modifica.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                items(list.animeIds, key = { it }) { id ->
                    val anime = animeMap[id]
                    if (anime == null) {
                        Text(
                            "Caricamento…",
                            Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val watched = progress[id]?.watchedEpisodes?.size ?: 0
                        val total = anime.availableEpisodes
                        val complete = anime.isSeasonComplete(watched)
                        AnimeRow(
                            anime = anime,
                            onClick = { onOpenAnime(id) },
                            trailing = {
                                if (complete) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Stagione vista",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (canEdit) {
                                    IconButton(onClick = { vm.removeFromList(list.id, id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Rimuovi dalla lista")
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (total > 0) "$watched/$total puntate viste" else "Puntate non ancora disponibili",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (total > 0) {
                                LinearProgressIndicator(
                                    progress = { (watched.toFloat() / total).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
