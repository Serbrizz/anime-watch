@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.recs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.Anime
import com.example.animewatch.domain.RecommendationEngine
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AddToListDialog
import com.example.animewatch.ui.components.AnimeRow
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.ScreenBar
import kotlin.math.roundToInt

@Composable
fun RecommendationsScreen(
    appVm: AppViewModel,
    vm: RecommendationsViewModel,
    onOpenAnime: (Int) -> Unit,
) {
    val progress by appVm.progress.collectAsStateWithLifecycle()
    val lists by appVm.lists.collectAsStateWithLifecycle()
    val animeMap by appVm.anime.collectAsStateWithLifecycle()
    val franchises by appVm.franchises.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var useLlm by rememberSaveable { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<Anime?>(null) }

    val profile = remember(progress) { RecommendationEngine.buildProfile(progress.values) }
    val topGenres = remember(profile) { RecommendationEngine.topGenres(profile, 3) }
    // Non si consiglia ciò che è già in una lista o ha puntate viste, né le altre stagioni della stessa serie.
    val known = remember(progress, lists) { progress.keys + lists.flatMap { it.animeIds } }
    val excluded = remember(known, franchises) { known + known.flatMap { franchises[it]?.seasonIds.orEmpty() } }

    fun reload() {
        if (topGenres.isEmpty()) return
        val titles = progress.keys.mapNotNull { animeMap[it]?.englishOrRomaji }.take(10)
        vm.load(progress.values, titles, excluded, useLlm)
    }

    LaunchedEffect(known) { appVm.ensureFranchises(known) }
    // Si ricalcola solo se cambiano i generi dominanti (o l'interruttore AI), non a ogni puntata:
    // ogni calcolo costa richieste ad AniList.
    LaunchedEffect(topGenres, useLlm) { reload() }

    ScreenBar("Consigliati") {
        IconButton(onClick = { reload() }, enabled = topGenres.isNotEmpty()) {
            Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
        }
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            if (topGenres.isEmpty()) {
                EmptyState("Segna qualche puntata come vista: i consigli si basano sui generi che guardi.")
                return@Column
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Basato sui tuoi generi: ${topGenres.joinToString(", ")}",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (vm.llmAvailable) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Affina con AI (OpenRouter)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = useLlm, onCheckedChange = { useLlm = it })
                    }
                }
            }

            val visible = state.items.filter { it.anime.id !in excluded }
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.error != null -> EmptyState(state.error.orEmpty())
                visible.isEmpty() -> EmptyState("Nessun consiglio al momento.")
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(visible, key = { it.anime.id }) { rec ->
                        AnimeRow(
                            anime = rec.anime,
                            onClick = { onOpenAnime(rec.anime.id) },
                            trailing = {
                                IconButton(onClick = { addTarget = rec.anime }) {
                                    Icon(Icons.Default.Add, contentDescription = "Aggiungi a una lista")
                                }
                            },
                        ) {
                            Text(
                                "Affinità ${(rec.score * 100).roundToInt()}% · ${rec.matchedGenres.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            rec.reason?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    addTarget?.let { anime ->
        AddToListDialog(appVm, anime, onDismiss = { addTarget = null })
    }
}
