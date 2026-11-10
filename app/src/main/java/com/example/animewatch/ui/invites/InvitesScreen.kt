@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.invites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.ListInvite
import com.example.animewatch.data.model.ListRole
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.EmptyState

@Composable
fun InvitesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val invites by vm.incomingInvites.collectAsStateWithLifecycle()
    val accepting by vm.accepting.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inviti") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        if (invites.isEmpty()) {
            EmptyState(
                "Nessun invito. Quando qualcuno condivide una lista con la tua email Google, la trovi qui.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(invites, key = { it.id }) { invite ->
                    InviteCard(
                        invite = invite,
                        busy = accepting == invite.id,
                        enabled = accepting == null,
                        onAccept = { vm.acceptInvite(invite) },
                        onDecline = { vm.declineInvite(invite) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteCard(
    invite: ListInvite,
    busy: Boolean,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(invite.listName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${invite.fromName.ifBlank { "Qualcuno" }} ti invita a " +
                    if (invite.role == ListRole.EDITOR) "collaborare: potrai aggiungere e togliere anime."
                    else "visualizzare la lista.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
            ) {
                OutlinedButton(onClick = onDecline, enabled = enabled) { Text("Rifiuta") }
                Button(onClick = onAccept, enabled = enabled) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                    } else {
                        Text("Accetta")
                    }
                }
            }
        }
    }
}
