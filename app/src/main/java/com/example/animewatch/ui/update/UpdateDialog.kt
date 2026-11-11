package com.example.animewatch.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Nuova versione disponibile: note della release e, durante il download, l'avanzamento. Mentre scarica non si chiude. */
@Composable
fun UpdateDialog(state: UpdateState, currentVersion: String, onInstall: () -> Unit, onDismiss: () -> Unit) {
    val release = state.available ?: return
    val busy = state.downloading
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text("Nuova versione disponibile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val size = if (release.size > 0) " Download: %.1f MB.".format(release.size / 1_048_576.0) else ""
                Text(
                    "È disponibile AnimeWatch ${release.version} (stai usando la $currentVersion). " +
                        "L'app scaricherà l'aggiornamento e aprirà l'installazione di Android.$size"
                )
                // Le note di ogni versione saltata, dalla più recente: si vede tutto ciò che cambia, non solo l'ultima.
                release.changes.filter { it.notes.isNotBlank() }.forEach { change ->
                    Spacer(Modifier.height(12.dp))
                    if (release.changes.size > 1) {
                        Text("Versione ${change.version}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(change.notes, style = MaterialTheme.typography.bodySmall)
                }
                if (busy) {
                    Spacer(Modifier.height(16.dp))
                    val progress = state.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Puoi aggiornare anche più tardi dal Profilo, in alto a destra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !busy) { Text(if (state.error != null) "Riprova" else "Aggiorna ora") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Più tardi") }
        },
    )
}
