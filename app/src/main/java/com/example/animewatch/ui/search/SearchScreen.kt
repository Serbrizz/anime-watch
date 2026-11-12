@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.Anime
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AddToListSheet
import com.example.animewatch.ui.components.AnimeRow
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.InListIcon
import com.example.animewatch.ui.components.ScreenBar
import com.example.animewatch.ui.components.SkeletonAnimeList

/**
 * Ricerca nel catalogo. Aperta da una lista ([targetListId], pulsante "Aggiungi anime") il "+" mette la serie
 * direttamente in quella lista e diventa una spunta che la toglie; altrimenti apre il pannello per scegliere le liste.
 */
@Composable
fun SearchScreen(
    appVm: AppViewModel,
    vm: SearchViewModel,
    onOpenAnime: (Int) -> Unit,
    targetListId: String? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    val lists by appVm.lists.collectAsStateWithLifecycle()
    val me by appVm.user.collectAsStateWithLifecycle()
    val franchises by appVm.franchises.collectAsStateWithLifecycle()
    var addTarget by remember { mutableStateOf<Anime?>(null) }
    val haptic = LocalHapticFeedback.current

    // Se intanto la lista sparisce o si perde il permesso di modifica, resta la ricerca normale.
    val targetList = targetListId
        ?.let { id -> lists.firstOrNull { it.id == id } }
        ?.takeIf { it.canEdit(me?.uid) }

    // Arrivando da una lista si vuole scrivere subito; solo la prima volta, non a ogni ritorno dalla scheda di un anime.
    val focus = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    if (targetListId != null && !focused) {
        LaunchedEffect(Unit) {
            focus.requestFocus()
            focused = true
        }
    }

    ScreenBar(targetList?.let { "Aggiungi a ${it.name}" } ?: "Cerca anime")

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Titolo (anche giapponese o inglese)") },
                singleLine = true,
            )
            when {
                state.loading -> SkeletonAnimeList(Modifier.padding(horizontal = 16.dp))
                state.error != null -> EmptyState(state.error.orEmpty())
                state.searched && state.results.isEmpty() -> EmptyState("Nessun risultato.")
                state.results.isEmpty() -> EmptyState(
                    if (targetList != null) {
                        "Scrivi almeno due lettere: quello che aggiungi con + finisce dritto in \"${targetList.name}\"."
                    } else {
                        "Scrivi almeno due lettere per cercare nel catalogo AniList."
                    }
                )
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    state.fallbackQuery?.let { shorter ->
                        item(key = "fallback") {
                            Text(
                                "Nessun titolo con tutte le parole: risultati per \"$shorter\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    items(series, key = { it.id }) { anime ->
                        AnimeRow(
                            anime = anime,
                            onClick = { onOpenAnime(anime.id) },
                            trailing = {
                                if (targetList != null) {
                                    val inList = targetList.containsSeries(anime.id, franchises)
                                    IconButton(onClick = {
                                        if (!inList) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        appVm.setInList(targetList, anime, add = !inList)
                                    }) {
                                        InListIcon(
                                            inList,
                                            addDescription = "Aggiungi a ${targetList.name}",
                                            removeDescription = "Togli da ${targetList.name}",
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { addTarget = anime }) {
                                        Icon(Icons.Default.Add, contentDescription = "Aggiungi a una lista")
                                    }
                                }
                            },
                        ) {
                            Text(
                                anime.genres.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
