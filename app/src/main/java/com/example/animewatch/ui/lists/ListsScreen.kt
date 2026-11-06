@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.EmptyState

@Composable
fun ListsScreen(vm: AppViewModel, onOpenList: (String) -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val animeMap by vm.anime.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<AnimeList?>(null) }

    // Servono le schede per sapere quante stagioni sono davvero concluse.
    LaunchedEffect(lists) { vm.ensureAnime(lists.flatMap { it.animeIds }) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Le mie liste") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuova lista")
            }
        },
    ) { padding ->
        if (lists.isEmpty()) {
            EmptyState(
                "Nessuna lista. Tocca + per crearne una, poi aggiungi anime dalla scheda Cerca.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(lists, key = { it.id }) { list ->
                    val seen = list.animeIds.count { id ->
                        animeMap[id]?.isSeasonComplete(progress[id]?.watchedEpisodes?.size ?: 0) == true
                    }
                    ElevatedCard(onClick = { onOpenList(list.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(list.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${list.animeIds.size} anime · $seen stagioni viste",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { toDelete = list }) {
                                Icon(Icons.Default.Delete, contentDescription = "Elimina lista")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nuova lista") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome (es. Da vedere in inverno)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.createList(name)
                        showCreate = false
                    },
                ) { Text("Crea") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Annulla") } },
        )
    }

    toDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare \"${list.name}\"?") },
            text = { Text("Verrà rimossa solo la lista: i progressi sulle puntate restano salvati.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteList(list.id)
                    toDelete = null
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } },
        )
    }
}
