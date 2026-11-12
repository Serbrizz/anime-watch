package com.example.animewatch.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.ui.components.InkButton
import com.example.animewatch.ui.components.InkCard
import com.example.animewatch.ui.components.InkOutlinedButton
import com.example.animewatch.ui.components.ScreenBar

/**
 * Versione installata e, se c'è, quella a cui si aggiorna. "Aggiorna" apre il popup con le note e il download;
 * senza aggiornamenti c'è "Verifica aggiornamenti" (l'app controlla comunque da sola).
 */
@Composable
fun UpdatesScreen(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val available = state.available

    ScreenBar("Aggiornamenti")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AnimeWatch", style = MaterialTheme.typography.titleMedium)
                VersionRow("Versione installata", vm.currentVersion)
                if (available != null) {
                    VersionRow("Nuova versione", available.version, MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.size(4.dp))
                if (available != null) {
                    InkButton(
                        onClick = vm::openDialog,
                        enabled = !state.downloading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Aggiorna alla versione ${available.version}") }
                } else {
                    InkOutlinedButton(
                        onClick = vm::checkNow,
                        enabled = !state.checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Verifica aggiornamenti")
                    }
                }
            }
        }
        Text(
            "L'app controlla anche da sola se è uscita una nuova versione, quando la apri (al massimo ogni 6 ore).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VersionRow(label: String, version: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(version, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}
