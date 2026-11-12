@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.recs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import com.example.animewatch.domain.Recommendation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.Anime
import com.example.animewatch.domain.LlmReranker
import com.example.animewatch.domain.RecommendationEngine
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AddToListSheet
import com.example.animewatch.ui.components.AnimeRow
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.ScreenBar
import com.example.animewatch.ui.components.SkeletonAnimeList
import com.example.animewatch.ui.recs.RecommendationsViewModel.AiStatus
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
    val aiStatus by vm.aiStatus.collectAsStateWithLifecycle()
    var useLlm by rememberSaveable { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<Anime?>(null) }

    val profile = remember(progress) { RecommendationEngine.buildProfile(progress.values) }
    val topGenres = remember(profile) { RecommendationEngine.topGenres(profile, 3) }
    // Non si consiglia ciò che è già in una lista o ha puntate viste, né le altre stagioni della stessa serie.
    val known = remember(progress, lists) { progress.keys + lists.flatMap { it.animeIds } }
    val excluded = remember(known, franchises) { known + known.flatMap { franchises[it]?.seasonIds.orEmpty() } }

    fun reload(force: Boolean = false) {
        if (topGenres.isEmpty()) return
        val titles = LlmReranker.describeWatched(progress.values) { animeMap[it]?.englishOrRomaji }
        vm.load(progress.values, titles, excluded, useLlm, force)
    }

    // Preflight a ogni apertura: il credito dell'AI può finire o tornare (si azzera ogni giorno).
    LaunchedEffect(Unit) { vm.checkAiIfNeeded() }
    LaunchedEffect(known) { appVm.ensureFranchises(known) }
    // Il servizio tiene i consigli finché non si inizia un anime nuovo: riaprire la schermata o segnare altre puntate
    // non costa richieste. "Aggiorna" forza il ricalcolo.
    val started = remember(progress) { progress.filterValues { it.watchedEpisodes.isNotEmpty() }.keys }
    LaunchedEffect(started, useLlm) { reload() }

    ScreenBar("Consigliati") {
        IconButton(onClick = { vm.checkAi(); reload(force = true) }, enabled = topGenres.isNotEmpty()) {
            Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(Modifier.padding(padding)) {
            if (topGenres.isEmpty()) {
                EmptyState("Segna qualche puntata come vista: i consigli si basano sui generi che guardi.")
                return@Column
            }

            // Altezza fissa: quando il pulsante si stringe a cerchio non deve spostarsi niente. Chiuso, il cerchio sta
            // in colonna con i "+" delle righe.
            if (vm.llmAvailable) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (aiStatus == AiStatus.UNAVAILABLE) {
                            Text(
                                "Gemini non è disponibile al momento",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                            )
                        }
                    }
                    // Durante il preflight si può già accendere: il caricamento ne attende l'esito. A caricamento finito
                    // senza l'AI (errore, nessun candidato) torna aperto: Gemini non ha avuto niente da riordinare.
                    val active = useLlm && aiStatus != AiStatus.UNAVAILABLE && (state.loading || state.llmUsed)
                    GeminiButton(
                        active = active,
                        loading = state.loading,
                        done = active && !state.loading,
                        enabled = aiStatus != AiStatus.UNAVAILABLE,
                        // Se è rimasto acceso ma l'AI non è partita, ritoccarlo riprova.
                        onToggle = { on -> if (on && useLlm) reload() else useLlm = on },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            val visible = state.items.filter { it.anime.id !in excluded }
            // Le righe hanno una chiave, quindi cambiando elenco (AI accesa o spenta) la lista resterebbe sulla riga che
            // era in cima, nascondendo il nuovo primo consiglio: si torna all'inizio.
            val listState = rememberLazyListState()
            LaunchedEffect(state.items) { listState.scrollToItem(0) }
            // Mentre si ricalcola resta l'elenco di prima, sbiadito: l'attesa la mostra già l'anello sul pulsante Gemini.
            val listAlpha by animateFloatAsState(if (state.loading) 0.4f else 1f, label = "listAlpha")
            when {
                state.loading && visible.isEmpty() -> SkeletonAnimeList(Modifier.padding(horizontal = 16.dp))
                state.error != null -> EmptyState(state.error.orEmpty())
                visible.isEmpty() -> EmptyState("Nessun consiglio al momento.")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.alpha(listAlpha),
                ) {
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
                            AffinityText(rec, fromGemini = state.llmUsed)
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
        AddToListSheet(appVm, anime, onDismiss = { addTarget = null })
    }
}

/** "Affinità 90% · generi", con la stella di Gemini davanti se l'affinità l'ha data l'AI. */
@Composable
private fun AffinityText(rec: Recommendation, fromGemini: Boolean) {
    val text = buildAnnotatedString {
        if (fromGemini) {
            appendInlineContent(GEMINI_MARK, "Gemini: ")
            append(" ")
        }
        append("Affinità ${(rec.score * 100).roundToInt()}% · ${rec.matchedGenres.joinToString(", ")}")
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        inlineContent = mapOf(
            GEMINI_MARK to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                GeminiLogo(green = 0f, modifier = Modifier.fillMaxSize())
            },
        ),
    )
}

private const val GEMINI_MARK = "gemini"
