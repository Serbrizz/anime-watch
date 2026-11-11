package com.example.animewatch.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Forma di attesa al posto di un contenuto che sta arrivando: grigia e pulsante. Tutte quelle a schermo pulsano
 * allo stesso ritmo, perché l'animazione ha la stessa durata e parte dal primo fotogramma.
 */
fun Modifier.skeleton(shape: Shape = RoundedCornerShape(6.dp)): Modifier = composed {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    clip(shape)
        .graphicsLayer { alpha = pulse }
        .background(MaterialTheme.colorScheme.surfaceVariant)
}

/**
 * [lines] righe di testo in attesa, alte come quelle di [style] (l'ultima più corta): quando il testo arriva la
 * pagina non si sposta.
 */
@Composable
fun SkeletonText(lines: Int, style: TextStyle, modifier: Modifier = Modifier, lastLineFraction: Float = 0.6f) {
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Column(modifier) {
        repeat(lines) { index ->
            Box(Modifier.height(lineHeight).fillMaxWidth(if (index == lines - 1) lastLineFraction else 1f)) {
                // Lo spazio fra le righe resta vuoto, come fra le righe vere.
                Box(Modifier.fillMaxWidth().height(lineHeight * 0.7f).align(Alignment.Center).skeleton())
            }
        }
    }
}

/** Una riga di [AnimeRow] in attesa: copertina e due righe di testo. */
@Composable
fun SkeletonAnimeRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 56.dp, height = 80.dp).skeleton(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonText(1, MaterialTheme.typography.titleMedium, lastLineFraction = 0.7f)
            SkeletonText(1, MaterialTheme.typography.bodyMedium, lastLineFraction = 0.45f)
        }
    }
}

/** Un elenco di [count] righe in attesa, al posto dei risultati che stanno arrivando. */
@Composable
fun SkeletonAnimeList(modifier: Modifier = Modifier, count: Int = 6) {
    Column(modifier) {
        repeat(count) { SkeletonAnimeRow() }
    }
}
