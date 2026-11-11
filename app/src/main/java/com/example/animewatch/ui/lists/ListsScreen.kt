@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
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
import com.example.animewatch.data.model.ListRole
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.roleLabel
import com.example.animewatch.ui.update.UpdateViewModel

@Composable
fun ListsScreen(
    vm: AppViewModel,
    updateVm: UpdateViewModel,
    onOpenList: (String) -> Unit,
    onOpenInvites: () -> Unit,
) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val animeMap by vm.anime.collectAsStateWithLifecycle()
    val invites by vm.incomingInvites.collectAsStateWithLifecycle()
    val me by vm.user.collectAsStateWithLifecycle()
    val update by updateVm.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<AnimeList?>(null) }

    // Servono le schede per sapere quante stagioni sono davvero concluse.
    LaunchedEffect(lists) { vm.ensureAnime(lists.flatMap { it.animeIds }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Le mie liste") },
                actions = {
                    IconButton(onClick = onOpenInvites) {
                        BadgedBox(badge = { if (invites.isNotEmpty()) Badge { Text("${invites.size}") } }) {
                            Icon(Icons.Default.Email, contentDescription = "Inviti")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            BadgedBox(badge = { if (update.available != null) Badge() }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Altre opzioni")
                            }
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val available = update.available
                            if (available != null) {
                                DropdownMenuItem(
                                    text = { Text("Aggiorna alla versione ${available.version}") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = { menuOpen = false; updateVm.openDialog() },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Verifica aggiornamenti") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = { menuOpen = false; updateVm.checkNow() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Esci dall'account") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                                onClick = { menuOpen = false; vm.signOut() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("AnimeWatch ${updateVm.currentVersion}") },
                                onClick = {},
                                enabled = false,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuova lista")
            }
        },
    ) { padding ->
        if (lists.isEmpty() && invites.isEmpty()) {
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
                if (invites.isNotEmpty()) {
                    item(key = "invites") {
                        Card(
                            onClick = onOpenInvites,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (invites.size == 1) "Hai un invito a una lista condivisa"
                                    else "Hai ${invites.size} inviti a liste condivise",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }
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
