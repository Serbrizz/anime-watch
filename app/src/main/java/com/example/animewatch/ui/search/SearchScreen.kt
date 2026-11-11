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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.Anime
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.AddToListDialog
import com.example.animewatch.ui.components.AnimeRow
import com.example.animewatch.ui.components.EmptyState

@Composable
fun SearchScreen(
    appVm: AppViewModel,
    vm: SearchViewModel,
    onOpenAnime: (Int) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var addTarget by remember { mutableStateOf<Anime?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Cerca anime") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Titolo (anche giapponese o inglese)") },
                singleLine = true,
            )
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.error != null -> EmptyState(state.error.orEmpty())
                state.searched && state.results.isEmpty() -> EmptyState("Nessun risultato.")
                state.results.isEmpty() -> EmptyState("Scrivi almeno due lettere per cercare nel catalogo AniList.")
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(state.results, key = { it.id }) { anime ->
                        AnimeRow(
                            anime = anime,
                            onClick = { onOpenAnime(anime.id) },
                            trailing = {
                                IconButton(onClick = { addTarget = anime }) {
                                    Icon(Icons.Default.Add, contentDescription = "Aggiungi a una lista")
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
        AddToListDialog(appVm, anime, onDismiss = { addTarget = null })
    }
}
