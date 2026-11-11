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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.Franchise
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AddToListSheet
import com.example.animewatch.ui.components.CoverImage
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.ScreenBar
import com.example.animewatch.ui.components.statusLabel

@Composable
fun AnimeDetailScreen(vm: AppViewModel, animeId: Int) {
    val animeMap by vm.anime.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val franchises by vm.franchises.collectAsStateWithLifecycle()
    // Si apre sulla stagione richiesta; il selettore cambia solo quella mostrata, non la rotta.
    var selectedId by rememberSaveable(animeId) { mutableIntStateOf(animeId) }
    val anime = animeMap[selectedId]
    val seasons = (franchises[animeId]?.seasonIds ?: listOf(animeId)).mapNotNull { animeMap[it] }
    val labels = remember(seasons) { Franchise.seasonLabels(seasons) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable(selectedId) { mutableStateOf(false) }

    LaunchedEffect(animeId) {
        vm.ensureAnime(listOf(animeId))
        vm.ensureFranchises(listOf(animeId))
    }

    ScreenBar(seasons.firstOrNull()?.englishOrRomaji ?: anime?.englishOrRomaji ?: "")

    Scaffold { padding ->
        if (anime == null) {
            EmptyState("Caricamento…", Modifier.padding(padding))
            return@Scaffold
        }

        val watched = progress[anime.id]?.watchedEpisodes.orEmpty()
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
                        Text(anime.englishOrRomaji, style = MaterialTheme.typography.titleLarge)
                        // Il romaji sotto, se diverso: serve a riconoscere la serie e a cercarla altrove.
                        if (anime.englishOrRomaji != anime.title) {
                            Text(
                                anime.title,
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
                OutlinedButton(onClick = { showAddSheet = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Aggiungi a una lista")
                }
            }

            // Selettore di stagione: le puntate sotto sono quelle della stagione scelta.
            if (seasons.size > 1) {
                item(span = fullWidth) {
                    SeasonSelector(
                        seasons = seasons,
                        labels = labels,
                        selectedId = selectedId,
                        isSeen = { it.isSeasonComplete(progress[it.id]?.watchedEpisodes?.size ?: 0) },
                        onSelect = { selectedId = it },
                    )
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

        if (showAddSheet) {
            AddToListSheet(vm, anime, onDismiss = { showAddSheet = false })
        }
    }
}

/** Menu a tendina con le stagioni della serie: etichetta (S1, Film…), titolo della scheda e spunta se vista. */
@Composable
private fun SeasonSelector(
    seasons: List<Anime>,
    labels: List<String>,
    selectedId: Int,
    isSeen: (Anime) -> Boolean,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = seasons.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = labels[selected],
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Stagione") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            seasons.forEachIndexed { i, season ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(labels[i], style = MaterialTheme.typography.titleSmall)
                            Text(
                                season.englishOrRomaji,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    trailingIcon = if (isSeen(season)) {
                        { Icon(Icons.Default.Check, contentDescription = "Vista", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(season.id)
                        open = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
