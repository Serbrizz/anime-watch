@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.ListRole
import com.example.animewatch.ui.theme.Night

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

/**
 * Foto dell'account. Finché non è caricata, se non si carica (offline) o se l'account non ne ha una (un metodo di
 * accesso diverso da Google potrebbe non fornirla) resta l'icona generica di una persona.
 */
@Composable
fun UserAvatar(photoUrl: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    var loaded by remember(photoUrl) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.6f),
            )
        }
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Copertina con il contorno a china, come le immagini disegnate. */
@Composable
fun CoverImage(url: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, Night.Ink, shape),
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
                anime.englishOrRomaji,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            content()
        }
        trailing()
    }
}
