@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.ListRole

fun statusLabel(status: String?): String = when (status) {
    "FINISHED" -> "Concluso"
    "RELEASING" -> "In corso"
    "NOT_YET_RELEASED" -> "Non ancora uscito"
    "CANCELLED" -> "Cancellato"
    "HIATUS" -> "In pausa"
    else -> "Stato sconosciuto"
}

fun roleLabel(role: ListRole): String = when (role) {
    ListRole.OWNER -> "Proprietario"
    ListRole.EDITOR -> "Modifica"
    ListRole.VIEWER -> "Sola visualizzazione"
}

/** Foto dell'account Google; se manca, l'iniziale del nome su un cerchio colorato. */
@Composable
fun MemberAvatar(name: String, photoUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun CoverImage(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Riga standard: copertina, titolo, contenuto libero sotto il titolo e azione a destra. */
@Composable
fun AnimeRow(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(anime.coverUrl, Modifier.size(width = 56.dp, height = 80.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                anime.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            content()
        }
        trailing()
    }
}

/** Spunta/togli un anime da ciascuna lista. */
@Composable
fun AddToListDialog(
    anime: Anime,
    lists: List<AnimeList>,
    onToggle: (list: AnimeList, add: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi a lista") },
        text = {
            if (lists.isEmpty()) {
                Text("Non hai liste in cui aggiungere anime: creane una dalla scheda Liste.")
            } else {
                Column {
                    lists.forEach { list ->
                        val inList = anime.id in list.animeIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(list, !inList) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = inList, onCheckedChange = { onToggle(list, it) })
                            Text(list.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fatto") } },
    )
}
