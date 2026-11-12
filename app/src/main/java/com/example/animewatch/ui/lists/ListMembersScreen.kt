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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.ListInvite
import com.example.animewatch.data.model.ListRole
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.EmptyState
import com.example.animewatch.ui.components.InkButton
import com.example.animewatch.ui.components.InkOutlinedButton
import com.example.animewatch.ui.components.InkSegment
import com.example.animewatch.ui.components.ScreenBar
import com.example.animewatch.ui.components.UserAvatar
import com.example.animewatch.ui.components.roleLabel
import kotlinx.coroutines.flow.flowOf

@Composable
fun ListMembersScreen(vm: AppViewModel, listId: String, onLeft: () -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val me by vm.user.collectAsStateWithLifecycle()
    val list = lists.firstOrNull { it.id == listId }
    val isOwner = list?.isOwner(me?.uid) == true
    val pending by remember(listId, isOwner) { if (isOwner) vm.outgoingInvites(listId) else flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    var confirmLeave by remember { mutableStateOf(false) }

    ScreenBar(list?.name?.let { "Condivisione · $it" } ?: "Condivisione")

    Scaffold(containerColor = Color.Transparent) { padding ->
        if (list == null) {
            EmptyState("Lista non trovata.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isOwner) {
                item(key = "invite") {
                    InviteForm(
                        myEmail = me?.email.orEmpty(),
                        onSend = { email, role -> vm.sendInvite(list, email, role) },
                    )
                }
                if (pending.isNotEmpty()) {
                    item(key = "pendingTitle") { SectionTitle("Inviti in attesa") }
                    items(pending, key = { "invite_${it.id}" }) { invite ->
                        PendingInviteRow(invite, onRevoke = { vm.revokeInvite(invite) })
                    }
                }
            }

            item(key = "membersTitle") { SectionTitle("Membri (${list.members.size})") }
            // Prima il proprietario, poi gli altri in ordine di nome.
            val members = list.members.entries
                .sortedWith(compareBy({ it.value != ListRole.OWNER }, { list.profiles[it.key]?.name.orEmpty() }))
            items(members, key = { "member_${it.key}" }) { (uid, role) ->
                MemberRow(
                    list = list,
                    uid = uid,
                    role = role,
                    isMe = uid == me?.uid,
                    canManage = isOwner && role != ListRole.OWNER,
                    onSetRole = { vm.setMemberRole(list, uid, it) },
                    onRemove = { vm.removeMember(list, uid) },
                )
            }

            if (!isOwner) {
                item(key = "leave") {
                    Spacer(Modifier.padding(top = 8.dp))
                    InkOutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Esci dalla lista")
                    }
                }
            }
        }
    }

    if (confirmLeave && list != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Uscire da \"${list.name}\"?") },
            text = { Text("Per rientrare servirà un nuovo invito. I tuoi progressi sulle puntate restano salvati.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.deleteOrLeaveList(list)
                    onLeft()
                }) { Text("Esci") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun InviteForm(myEmail: String, onSend: (email: String, role: ListRole) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(ListRole.EDITOR) }
    var sent by rememberSaveable { mutableStateOf<String?>(null) }
    val clean = email.trim().lowercase()
    val error = when {
        clean.isEmpty() -> null
        !ListInvite.isPlausibleEmail(clean) -> "Email non valida"
        clean == myEmail -> "Non puoi invitare te stesso"
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Invita qualcuno")
        Text(
            "Scrivi l'email dell'account Google della persona: l'invito comparirà fra gli inviti del suo Profilo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                sent = null
            },
            label = { Text("Email Google") },
            singleLine = true,
            isError = error != null,
            supportingText = {
                when {
                    error != null -> Text(error)
                    sent != null -> Text("Invito inviato a $sent")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ListRole.EDITOR, ListRole.VIEWER).forEach { option ->
                InkSegment(selected = role == option, onClick = { role = option }) {
                    Text(if (option == ListRole.EDITOR) "Può modificare" else "Può solo vedere")
                }
            }
        }
        InkButton(
            onClick = {
                onSend(clean, role)
                sent = clean
                email = ""
            },
            enabled = clean.isNotEmpty() && error == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Invia invito") }
    }
}

@Composable
private fun PendingInviteRow(invite: ListInvite, onRevoke: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(invite.toEmail, style = MaterialTheme.typography.bodyLarge)
            Text(
                "In attesa · ${roleLabel(invite.role)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRevoke) { Icon(Icons.Default.Close, contentDescription = "Ritira invito") }
    }
    HorizontalDivider()
}

@Composable
private fun MemberRow(
    list: AnimeList,
    uid: String,
    role: ListRole,
    isMe: Boolean,
    canManage: Boolean,
    onSetRole: (ListRole) -> Unit,
    onRemove: () -> Unit,
) {
    val profile = list.profiles[uid]
    val name = profile?.name ?: "Utente"
    var menu by remember { mutableStateOf(false) }

    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(profile?.photoUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isMe) "$name (tu)" else name, style = MaterialTheme.typography.bodyLarge)
            Text(
                roleLabel(role),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canManage) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Gestisci $name")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val other = if (role == ListRole.EDITOR) ListRole.VIEWER else ListRole.EDITOR
                    DropdownMenuItem(
                        text = { Text(if (other == ListRole.EDITOR) "Permetti di modificare" else "Solo visualizzazione") },
                        onClick = {
                            menu = false
                            onSetRole(other)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rimuovi dalla lista") },
                        onClick = {
                            menu = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}
