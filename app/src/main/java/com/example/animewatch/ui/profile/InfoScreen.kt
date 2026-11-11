package com.example.animewatch.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.animewatch.BuildConfig
import com.example.animewatch.R
import com.example.animewatch.ui.components.ScreenBar
import com.example.animewatch.ui.recs.GeminiLogo

/**
 * Versione dell'app e fonti dei dati, ognuna col suo logo; toccandone una si apre il suo sito.
 * Il logo e la frase di TMDB sono richiesti dai termini d'uso della sua API.
 */
@Composable
fun InfoScreen() {
    ScreenBar("Informazioni")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppHeader()
        Spacer(Modifier.height(12.dp))

        Text(
            "Fonti dei dati",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SourceCard(
            name = "AniList",
            role = "Catalogo degli anime",
            url = "https://anilist.co",
            text = "Ricerca, stagioni e serie collegate, generi, voto medio e stato delle uscite.",
            logo = { Image(painterResource(R.drawable.anilist_logo), contentDescription = null) },
        )
        SourceCard(
            name = "TMDB",
            role = "Puntate e trame",
            url = "https://www.themoviedb.org",
            text = "Titoli, durate, immagini e voti di ogni puntata, e le trame in italiano.",
            note = "Questo prodotto usa l'API di TMDB ma non è approvato né certificato da TMDB.",
            logo = {
                Image(
                    painterResource(R.drawable.tmdb_logo_square),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                )
            },
        )
        SourceCard(
            name = "Gemini",
            role = "Consigli con l'AI",
            url = "https://gemini.google.com",
            text = "Riordina i consigliati con \"Affina con Gemini\". Riceve generi e titoli degli anime che hai " +
                "visto, non il tuo nome né la tua email.",
            logo = { GeminiLogo(green = 0f, modifier = Modifier.size(32.dp)) },
        )
    }
}

/** Icona dell'app (come nel launcher: primo piano sul suo sfondo, ritagliato), nome, versione. */
@Composable
private fun AppHeader() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            // Il primo piano di un'icona adattiva è 108 dp, di cui si vedono i 72 centrali: si ingrandisce di 1,5.
            Image(
                painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(132.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("AnimeWatch", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Versione ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Le tue liste di anime, puntata per puntata.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SourceCard(
    name: String,
    role: String,
    url: String,
    text: String,
    logo: @Composable () -> Unit,
    note: String? = null,
) {
    val uriHandler = LocalUriHandler.current
    OutlinedCard(onClick = { runCatching { uriHandler.openUri(url) } }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { logo() }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Apri il sito di $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
