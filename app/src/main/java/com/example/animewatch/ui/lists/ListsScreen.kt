package com.example.animewatch.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.animewatch.data.model.Franchise
import com.example.animewatch.data.model.ListRole
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.ScreenBar
import com.example.animewatch.ui.components.roleLabel

@Composable
fun ListsScreen(vm: AppViewModel, onOpenList: (String) -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val listsLoaded by vm.listsLoaded.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val animeMap by vm.anime.collectAsStateWithLifecycle()
    val me by vm.user.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<AnimeList?>(null) }

    val franchises by vm.franchises.collectAsStateWithLifecycle()
    val unresolved by vm.unresolved.collectAsStateWithLifecycle()

    // Servono tutte le stagioni di ogni serie per sapere quante sono davvero concluse.
    LaunchedEffect(lists) { vm.ensureFranchises(lists.flatMap { it.animeIds }) }

    ScreenBar("Le mie liste")

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuova lista")
            }
        },
    ) { padding ->
        if (!listsLoaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (lists.isEmpty()) {
            EmptyState(
                "Nessuna lista. Tocca + per crearne una, poi aggiungi anime dalla scheda Cerca. " +
                    "Gli inviti alle liste condivise li trovi nel Profilo, in alto a destra.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(lists, key = { it.id }) { list ->
                    val groups = Franchise.groupList(list.animeIds, franchises)
                    val seen = groups.flatMap { it.seasonIds }.count { id ->
                        animeMap[id]?.isSeasonComplete(progress[id]?.watchedEpisodes?.size ?: 0) == true
                    }
                    // Le stagioni arrivano a ondate: i conteggi si mostrano solo a serie tutte ricostruite,
                    // altrimenti crescono sotto gli occhi. Chi non si è potuto risolvere non tiene in attesa.
                    val loading = list.animeIds.any { it !in franchises && it !in unresolved }
                    ElevatedCard(onClick = { onOpenList(list.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(list.name, style = MaterialTheme.typography.titleMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val style = MaterialTheme.typography.bodyMedium
                                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                                    Text("${groups.size} anime · ", style = style, color = color)
                                    if (loading) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Text(" stagioni viste", style = style, color = color)
                                    } else {
                                        Text("$seen stagioni viste", style = style, color = color)
                                    }
                                }
                                SharedMarker(list, me?.uid)
                            }
                            IconButton(onClick = { toDelete = list }) {
                                if (list.isOwner(me?.uid)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Elimina lista")
                                } else {
                                    Icon(Icons.Default.Close, contentDescription = "Esci dalla lista")
                                }
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
        val owner = list.isOwner(me?.uid)
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(if (owner) "Eliminare \"${list.name}\"?" else "Uscire da \"${list.name}\"?") },
            text = {
                Text(
                    when {
                        !owner -> "La lista sparirà dalle tue liste; per rientrare servirà un nuovo invito. " +
                            "I tuoi progressi sulle puntate restano salvati."
                        list.isShared -> "La lista verrà eliminata anche per gli altri membri. " +
                            "I progressi sulle puntate restano salvati."
                        else -> "Verrà rimossa solo la lista: i progressi sulle puntate restano salvati."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteOrLeaveList(list)
                    toDelete = null
                }) { Text(if (owner) "Elimina" else "Esci") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } },
        )
    }
}

/** Contrassegno delle liste condivise: chi l'ha condivisa (o quanti membri) e il proprio ruolo. */
@Composable
private fun SharedMarker(list: AnimeList, myUid: String?) {
    val role = list.roleOf(myUid) ?: return
    if (!list.isShared && role == ListRole.OWNER) return
    val text = if (role == ListRole.OWNER) {
        "Condivisa · ${list.members.size} membri"
    } else {
        val owner = list.profiles[list.ownerId]?.name ?: "un altro utente"
        "Condivisa da $owner · ${roleLabel(role)}"
    }
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
