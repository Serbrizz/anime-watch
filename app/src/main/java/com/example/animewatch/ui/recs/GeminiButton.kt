package com.example.animewatch.ui.recs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

private val GeminiGradient = listOf(Color(0xFF4796E3), Color(0xFF9177C7), Color(0xFFCA6673))
private val DoneGreen = Color(0xFF34A853)

/**
 * "Affina con Gemini": spento è un pulsante col logo e la scritta; acceso si riduce a un cerchio col solo logo,
 * con un anello mentre l'AI lavora e, a riordino fatto, il logo verde e una spunta che si disegna.
 *
 * [active]: l'utente l'ha acceso e l'AI è utilizzabile. [loading]: si stanno calcolando i consigli.
 * [done]: l'elenco mostrato arriva dall'AI.
 */
@Composable
fun GeminiButton(
    active: Boolean,
    loading: Boolean,
    done: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val green by animateFloatAsState(if (done) 1f else 0f, tween(400), label = "green")
    val borderColor by animateColorAsState(
        if (done) DoneGreen else MaterialTheme.colorScheme.outline,
        tween(400),
        label = "border",
    )
    // Logo di 20 dp: con 10 dp per lato il pulsante chiuso è un cerchio di 40, alto quanto quello aperto.
    val sidePadding by animateDpAsState(if (active) 10.dp else 14.dp, label = "padding")

    Box(
        modifier.semantics { if (done) stateDescription = "Consigli riordinati da Gemini" },
    ) {
        Surface(
            checked = active,
            onCheckedChange = onToggle,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Row(
                Modifier.height(40.dp).padding(horizontal = sidePadding).alpha(if (enabled) 1f else 0.38f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GeminiLogo(
                    green = green,
                    modifier = Modifier.size(20.dp).semantics {
                        contentDescription = if (active) "Togli affinamento Gemini" else "Affina con Gemini"
                    },
                )
                AnimatedVisibility(
                    visible = !active,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Affina con Gemini",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
        if (active && loading) {
            CircularProgressIndicator(Modifier.matchParentSize(), strokeWidth = 2.dp)
        }
        AnimatedVisibility(
            visible = done,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            AnimatedCheck(Modifier.size(18.dp))
        }
    }
}

/** La stella a quattro punte di Gemini, col suo gradiente che sfuma nel verde man mano che [green] va a 1. */
@Composable
fun GeminiLogo(green: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val star = sparkle(size.minDimension)
        drawPath(star, Brush.linearGradient(GeminiGradient, start = Offset.Zero, end = Offset(size.width, size.height)))
        if (green > 0f) drawPath(star, DoneGreen, alpha = green)
    }
}

/** Lati concavi: ogni curva parte da una punta in direzione del centro e arriva alla punta successiva. */
private fun sparkle(side: Float): Path {
    val c = side / 2
    val near = c * 0.55f // controllo vicino alla punta da cui si parte
    val far = c * 1.45f
    return Path().apply {
        moveTo(c, 0f)
        cubicTo(c, near, far, c, side, c)
        cubicTo(far, c, c, far, c, side)
        cubicTo(c, far, near, c, 0f, c)
        cubicTo(near, c, c, near, c, 0f)
        close()
    }
}

/** Pallino verde con una spunta bianca che si disegna da sinistra a destra. */
@Composable
private fun AnimatedCheck(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 350, delayMillis = 120)) }
    Canvas(modifier.background(DoneGreen, CircleShape)) {
        val s = size.minDimension
        val check = Path().apply {
            moveTo(s * 0.28f, s * 0.52f)
            lineTo(s * 0.44f, s * 0.68f)
            lineTo(s * 0.74f, s * 0.36f)
        }
        val measure = PathMeasure().apply { setPath(check, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.value, drawn, true)
        drawPath(drawn, Color.White, style = Stroke(width = s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
