package com.example.animewatch.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.components.InkButton
import com.example.animewatch.ui.components.InkCard
import com.example.animewatch.ui.components.ScreenBar
import com.example.animewatch.ui.components.UserAvatar
import com.example.animewatch.ui.components.inkPanel
import com.example.animewatch.ui.update.UpdateViewModel

/** Account (foto, nome, email), inviti ricevuti, aggiornamenti, informazioni sull'app e uscita. */
@Composable
fun ProfileScreen(
    appVm: AppViewModel,
    updateVm: UpdateViewModel,
    onOpenInvites: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val me by appVm.user.collectAsStateWithLifecycle()
    val invites by appVm.incomingInvites.collectAsStateWithLifecycle()
    val update by updateVm.state.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    ScreenBar("Profilo")

    // Le voci stanno in schede a china; dentro, le righe sono trasparenti e prendono il colore della scheda.
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UserAvatar(me?.photoUrl, size = 96.dp, modifier = Modifier.inkPanel(CircleShape, shadow = 4.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                me?.name?.takeIf { it.isNotBlank() } ?: "Il tuo account",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            me?.email?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        InkCard(Modifier.fillMaxWidth()) {
            ListItem(
                colors = itemColors,
                headlineContent = { Text("Inviti") },
                supportingContent = {
                    Text(
                        when (invites.size) {
                            0 -> "Nessun invito in attesa"
                            1 -> "1 invito a una lista condivisa"
                            else -> "${invites.size} inviti a liste condivise"
                        }
                    )
                },
                leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                trailingContent = { if (invites.isNotEmpty()) Badge { Text("${invites.size}") } },
                modifier = Modifier.clickable(onClick = onOpenInvites),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val available = update.available
            ListItem(
                colors = itemColors,
                headlineContent = { Text("Aggiornamenti") },
                supportingContent = {
                    Text(
                        if (available != null) "Versione ${updateVm.currentVersion} · disponibile la ${available.version}"
                        else "Versione ${updateVm.currentVersion}"
                    )
                },
                leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                trailingContent = {
                    if (available != null) {
                        InkButton(
                            onClick = updateVm::openDialog,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("Aggiorna") }
                    }
                },
                modifier = Modifier.clickable(onClick = onOpenUpdates),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                colors = itemColors,
                headlineContent = { Text("Informazioni") },
                supportingContent = { Text("Versione e fonti dei dati: AniList, TMDB, Gemini") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenInfo),
            )
        }
        Spacer(Modifier.height(16.dp))

        InkCard(Modifier.fillMaxWidth()) {
            ListItem(
                colors = itemColors,
                headlineContent = { Text("Esci dall'account", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable { confirmSignOut = true },
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Uscire dall'account?") },
            text = { Text("Liste e progressi restano salvati sul tuo account: li ritrovi al prossimo accesso.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    appVm.signOut()
                }) { Text("Esci") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Annulla") } },
        )
    }
}
