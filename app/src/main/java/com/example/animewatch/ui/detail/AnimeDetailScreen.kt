@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.animewatch.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AddToListDialog
import com.example.animewatch.ui.components.CoverImage
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.statusLabel

@Composable
fun AnimeDetailScreen(vm: AppViewModel, animeId: Int, onBack: () -> Unit) {
    val animeMap by vm.anime.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val anime = animeMap[animeId]
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(animeId) { vm.ensureAnime(listOf(animeId)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(anime?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        if (anime == null) {
            EmptyState("Caricamento…", Modifier.padding(padding))
            return@Scaffold
        }

        val watched = progress[animeId]?.watchedEpisodes.orEmpty()
        val total = anime.availableEpisodes
        val complete = anime.isSeasonComplete(watched.size)
        val fullWidth: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(64.dp),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Intestazione: copertina + dati principali
            item(span = fullWidth) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CoverImage(anime.coverUrl, Modifier.size(width = 120.dp, height = 170.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(anime.title, style = MaterialTheme.typography.titleLarge)
                        if (anime.titleEnglish != null && anime.titleEnglish != anime.title) {
                            Text(
                                anime.titleEnglish,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(statusLabel(anime.status), style = MaterialTheme.typography.bodyMedium)
                        val season = listOfNotNull(anime.season?.lowercase()?.replaceFirstChar { it.uppercase() }, anime.seasonYear?.toString())
                        if (season.isNotEmpty()) {
                            Text(season.joinToString(" "), style = MaterialTheme.typography.bodyMedium)
                        }
                        anime.averageScore?.let { Text("Voto medio: $it/100", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            if (anime.genres.isNotEmpty()) {
                item(span = fullWidth) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        anime.genres.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }

            anime.description?.takeIf { it.isNotBlank() }?.let { description ->
                item(span = fullWidth) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
            }

            item(span = fullWidth) {
                OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Aggiungi a una lista")
                }
            }

            // Progresso e azioni sulla stagione
            item(span = fullWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (total == 0) {
                        Text(
                            "Le puntate non sono ancora disponibili.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Viste: ${watched.size}/$total",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (complete) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(" Stagione vista", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { (watched.size.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!anime.isFinished) {
                            Text(
                                "Serie ancora in corso: la stagione risulta vista solo a serie conclusa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.markAllWatched(anime) },
                                enabled = watched.size < total,
                            ) { Text("Segna tutte come viste") }
                            OutlinedButton(
                                onClick = { vm.clearProgress(anime) },
                                enabled = watched.isNotEmpty(),
                            ) { Text("Azzera") }
                        }
                    }
                }
            }

            // Una chip per puntata
            items((1..total).toList(), key = { it }) { episode ->
                FilterChip(
                    selected = episode in watched,
                    onClick = { vm.toggleEpisode(anime, episode) },
                    label = { Text("$episode") },
                )
            }
        }

        if (showAddDialog) {
            AddToListDialog(
                anime = anime,
                lists = lists,
                onToggle = { list, add ->
                    if (add) vm.addToList(list.id, anime) else vm.removeFromList(list.id, anime.id)
                },
                onDismiss = { showAddDialog = false },
            )
        }
    }
}
