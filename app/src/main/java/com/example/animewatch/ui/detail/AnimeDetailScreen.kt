@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.animewatch.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.Episode
import com.example.animewatch.data.model.Franchise
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AddToListSheet
import com.example.animewatch.ui.components.CoverImage
import com.example.animewatch.ui.components.ScreenBar
import com.example.animewatch.ui.components.SkeletonText
import com.example.animewatch.ui.components.skeleton
import com.example.animewatch.ui.components.statusLabel
import java.util.Locale

@Composable
fun AnimeDetailScreen(vm: AppViewModel, animeId: Int) {
    val animeMap by vm.anime.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val franchises by vm.franchises.collectAsStateWithLifecycle()
    val resolving by vm.resolving.collectAsStateWithLifecycle()
    // Si apre sulla stagione richiesta; il selettore cambia solo quella mostrata, non la rotta.
    var selectedId by rememberSaveable(animeId) { mutableIntStateOf(animeId) }
    val anime = animeMap[selectedId]
    val franchise = franchises[animeId]
    val seasons = (franchise?.seasonIds ?: listOf(animeId)).mapNotNull { animeMap[it] }
    val labels = remember(seasons) { Franchise.seasonLabels(seasons) }
    // Il selettore c'è da subito se la scheda ha prequel o sequel: così l'arrivo delle stagioni non sposta la pagina.
    val hasSeasons = seasons.size > 1 || animeMap[animeId]?.relatedIds?.isNotEmpty() == true
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable(selectedId) { mutableStateOf(false) }

    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val episodesSettled by vm.episodesSettled.collectAsStateWithLifecycle()
    val unresolved by vm.unresolved.collectAsStateWithLifecycle()

    LaunchedEffect(animeId) {
        vm.ensureAnime(listOf(animeId))
        vm.ensureFranchises(listOf(animeId))
    }
    // Le puntate si chiedono per tutto il franchise (TMDB le dà insieme), quindi quando è noto.
    LaunchedEffect(franchise) {
        franchise?.let { vm.ensureEpisodes(it) }
    }

    ScreenBar(seasons.firstOrNull()?.englishOrRomaji ?: anime?.englishOrRomaji ?: "")

    Scaffold { padding ->
        if (anime == null) {
            DetailSkeleton(Modifier.padding(padding))
            return@Scaffold
        }
        // Puntate e trama italiana in arrivo: finché la ricerca non finisce (riuscita o no) si mostra lo skeleton, non la
        // trama inglese che poi cambierebbe. Non si aspetta se TMDB non è raggiungibile o il franchise non si risolve.
        val episodesPending = vm.episodesAvailable && episodes[anime.id] == null &&
            anime.id !in episodesSettled && animeId !in unresolved

        val watched = progress[anime.id]?.watchedEpisodes.orEmpty()
        val total = anime.availableEpisodes
        val known = episodes[anime.id]?.episodes.orEmpty().associateBy { it.number }
        val complete = anime.isSeasonComplete(watched.size)

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Intestazione: copertina + dati principali
            item {
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
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        anime.genres.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }

            // La trama in italiano di TMDB, se c'è; altrimenti quella inglese di AniList.
            if (episodesPending) {
                item { SkeletonText(5, MaterialTheme.typography.bodyMedium) }
            } else (episodes[anime.id]?.overview ?: anime.description)?.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
            }

            // Due voti da due fonti, su scale diverse: una riga per ciascuno, solo per quelli che ci sono.
            val episodeScores = known.values.any { it.score != null }
            if (episodeScores || anime.averageScore != null) {
                item { ScoreLegend(averageScore = anime.averageScore != null, episodeScores = episodeScores) }
            }

            item {
                OutlinedButton(onClick = { showAddSheet = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Aggiungi a una lista")
                }
            }

            // Selettore di stagione: le puntate sotto sono quelle della stagione scelta.
            if (hasSeasons) {
                item {
                    SeasonSelector(
                        seasons = seasons,
                        // Senza il franchise l'etichetta (S1, S2…) non si conosce: non si indovina.
                        labels = labels.takeIf { franchise != null },
                        loading = franchise == null && animeId in resolving,
                        selectedId = selectedId,
                        isSeen = { it.isSeasonComplete(progress[it.id]?.watchedEpisodes?.size ?: 0) },
                        onSelect = { selectedId = it },
                    )
                }
            }

            // Progresso e azioni sulla stagione
            item {
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

            // Una riga per puntata; titolo e miniatura arrivano dopo, senza cambiare l'altezza della riga.
            items((1..total).toList(), key = { it }) { number ->
                EpisodeRow(
                    number = number,
                    episode = known[number],
                    loading = episodesPending,
                    watched = number in watched,
                    onToggle = { vm.toggleEpisode(anime, number) },
                )
            }
        }

        if (showAddSheet) {
            AddToListSheet(vm, anime, onDismiss = { showAddSheet = false })
        }
    }
}

/** Da dove vengono i voti: il voto medio in alto è di AniList (0-100), le stelle delle puntate di TMDB (0-10). */
@Composable
private fun ScoreLegend(averageScore: Boolean, episodeScores: Boolean) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (averageScore) Text("Voto medio: utenti di AniList, da 0 a 100", style = style, color = color)
        if (episodeScores) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(" Voto della puntata: utenti di TMDB, da 0 a 10", style = style, color = color)
            }
        }
    }
}

/** La scheda mentre arriva: copertina, titolo e dati, generi e trama in attesa, con le misure di quelli veri. */
@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(width = 120.dp, height = 170.dp).skeleton(RoundedCornerShape(8.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonText(2, MaterialTheme.typography.titleLarge, lastLineFraction = 0.5f)
                SkeletonText(3, MaterialTheme.typography.bodyMedium, lastLineFraction = 0.4f)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Box(Modifier.size(width = 88.dp, height = 32.dp).skeleton(RoundedCornerShape(8.dp))) }
        }
        SkeletonText(5, MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Puntata: miniatura con la durata sopra, a destra "12. Titolo" (il numero in grigio, stessa dimensione) con sotto il
 * voto, centrati in verticale, e la casella. Senza titolo resta solo "Puntata 12". Tutta la riga segna o toglie la
 * puntata. Finché i dati della puntata non arrivano ([loading]) skeleton al posto di miniatura e titolo, finché non
 * arriva l'immagine sulla miniatura; poi il numero, se l'immagine non c'è. L'altezza non cambia quando arrivano i dati.
 */
@Composable
private fun EpisodeRow(number: Int, episode: Episode?, loading: Boolean, watched: Boolean, onToggle: () -> Unit) {
    val title = episode?.title
    val thumbnail = episode?.thumbnailUrl
    var imageLoading by remember(thumbnail) { mutableStateOf(thumbnail != null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = watched, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 128.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (loading || imageLoading) {
                Box(Modifier.matchParentSize().skeleton(RoundedCornerShape(8.dp)))
            } else {
                Text(
                    "$number",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            thumbnail?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Finita (riuscita o no) l'attesa sparisce: se l'immagine manca resta il numero.
                    onState = { imageLoading = it !is AsyncImagePainter.State.Success && it !is AsyncImagePainter.State.Error },
                    modifier = Modifier.matchParentSize(),
                )
            }
            episode?.length?.let { minutes ->
                Text(
                    "$minutes min",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            if (loading) {
                SkeletonText(2, MaterialTheme.typography.bodyMedium, lastLineFraction = 0.5f)
            } else if (title != null) {
                val grey = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = grey)) { append("$number. ") }
                        append(title)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text("Puntata $number", style = MaterialTheme.typography.bodyMedium)
            }
            if (episode != null) EpisodeNotes(episode)
        }
        // Lo stato lo porta la riga (toggleable): la casella è solo il disegno.
        Checkbox(checked = watched, onCheckedChange = null)
    }
}

/** Voto degli utenti di TMDB (0-10), se qualcuno ha votato. */
@Composable
private fun EpisodeNotes(episode: Episode) {
    val score = episode.score ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Star,
            contentDescription = "Voto",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            " " + String.format(Locale.ITALY, "%.1f", score),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Menu a tendina con le stagioni della serie: etichetta (S1, Film…), titolo della scheda e spunta se vista.
 * Finché le stagioni non sono note ([labels] null) mostra il titolo di quella aperta e non si apre;
 * durante il caricamento, al posto della freccia, un indicatore di attesa.
 */
@Composable
private fun SeasonSelector(
    seasons: List<Anime>,
    labels: List<String>?,
    loading: Boolean,
    selectedId: Int,
    isSeen: (Anime) -> Boolean,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = seasons.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    ExposedDropdownMenuBox(expanded = open && labels != null, onExpandedChange = { open = it && labels != null }) {
        OutlinedTextField(
            value = labels?.get(selected) ?: seasons.getOrNull(selected)?.englishOrRomaji.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Stagione") },
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    labels != null -> ExposedDropdownMenuDefaults.TrailingIcon(expanded = open)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        if (labels == null) return@ExposedDropdownMenuBox
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
