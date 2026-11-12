package com.example.animewatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.animewatch.ui.theme.Night

// Lo stile della finestra degli script portato in Compose: contorni "a china" e ombre nette, cioè un secondo bordo
// pieno spostato in basso a destra, senza sfumatura (le ombre di Material sono sfumate).

/**
 * Pannello con contorno a china e ombra netta di [shadow]. L'ombra si disegna fuori dai bordi: chi lo contiene deve
 * lasciarle spazio (5 dp in basso e a destra), altrimenti viene tagliata.
 */
fun Modifier.inkPanel(
    shape: Shape,
    color: Color = Night.Panel,
    shadow: Dp = 5.dp,
    border: Dp = 2.dp,
): Modifier = this
    .drawBehind {
        if (shadow > 0.dp) {
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(shadow.toPx(), shadow.toPx()) { drawOutline(outline, Night.Ink.copy(alpha = 0.85f)) }
        }
    }
    .border(border, Night.Ink, shape)
    .background(color, shape)
    .clip(shape)

/** Scheda: pannello con contorno e ombra; con [onClick] tutta la scheda si tocca. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Night.Panel,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .inkPanel(shape, color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides Night.Text) { content() }
    }
}

/**
 * Pulsante principale, come "Esegui" nella finestra degli script: colore pieno, contorno a china e ombra netta;
 * premuto scende sull'ombra. Disattivato perde colore e ombra: un arancione semitrasparente sul cielo diventerebbe
 * marrone, col testo scuro quasi illeggibile.
 */
@Composable
fun InkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Night.Accent,
    contentColor: Color = Night.AccentInk,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shift = 3.dp
    // Il pulsante occupa anche lo spazio dell'ombra, così nessuno la taglia; propagateMinConstraints fa riempire alla
    // faccia lo spazio chiesto da fuori (fillMaxWidth, height).
    Box(
        propagateMinConstraints = true,
        modifier = modifier
            .padding(end = shift, bottom = shift)
            .drawBehind { if (enabled) translate(shift.toPx(), shift.toPx()) { drawShape(shape, Night.Ink) } },
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .offset { if (pressed) IntOffset(shift.roundToPx(), shift.roundToPx()) else IntOffset.Zero }
                .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
                .then(
                    if (enabled) Modifier.border(2.dp, Night.Ink, shape).background(color, shape)
                    else Modifier.border(1.5.dp, Night.Line, shape).background(Night.Raised, shape),
                )
                .clip(shape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(contentPadding),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) contentColor else Night.Faint,
                LocalTextStyle provides MaterialTheme.typography.labelLarge,
            ) { content() }
        }
    }
}

/**
 * Pulsante secondario, come quelli della finestra degli script: fondo rialzato e bordo sottile, senza ombra.
 * Disattivato ha bordo e testo tenui, ma resta leggibile.
 */
@Composable
fun InkOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .border(1.5.dp, if (enabled) Night.Line else MaterialTheme.colorScheme.outlineVariant, shape)
            .background(Night.Raised, shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) Night.Text else Night.Faint,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) { content() }
    }
}

/**
 * Una scelta fra poche, come le opzioni a pulsanti della finestra degli script: pillola rialzata; accesa diventa
 * arancione piena, con il contorno a china.
 */
@Composable
fun InkSegment(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) Night.Ink else Night.Line, shape)
            .background(if (selected) Night.Accent else Night.Raised, shape)
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) Night.AccentInk else Night.Text,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) { content() }
    }
}

/** Etichetta che non si tocca (i generi): pillola rialzata con il bordo sottile. */
@Composable
fun InkTag(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Night.Text,
        modifier = modifier
            .border(1.5.dp, Night.Line, shape)
            .background(Night.Raised, shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

private fun DrawScope.drawShape(shape: Shape, color: Color) {
    drawOutline(shape.createOutline(size, layoutDirection, this), color)
}

/**
 * Cielo di notte dietro a tutta l'app: indaco in alto, orizzonte caldo in basso, qualche stella. Con [scenery] anche
 * la luna e le nuvole, come nella finestra degli script (nel login, dove nulla le copre).
 */
@Composable
fun NightSky(modifier: Modifier = Modifier, scenery: Boolean = false, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = Night.Sky))) {
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { (x, y, r) ->
                val center = Offset(size.width * x, size.height * y)
                if (r > 2f) drawPath(sparkle(center, r.dp.toPx()), Night.Moon.copy(alpha = 0.8f))
                else drawCircle(Night.Moon.copy(alpha = 0.55f), r.dp.toPx(), center)
            }
            if (scenery) {
                // Falce di luna: un disco chiaro coperto in parte da uno del colore del cielo.
                val moon = Offset(size.width * 0.78f, size.height * 0.11f)
                val radius = 22.dp.toPx()
                drawCircle(Night.Moon, radius, moon)
                drawCircle(Night.Sky[0].second, radius * 0.95f, moon + Offset(radius * 0.48f, -radius * 0.3f))
                drawCloud(Offset(-30.dp.toPx(), size.height), 260.dp.toPx(), 70.dp.toPx(), Color(0xFF2A2547))
                drawCloud(Offset(size.width * 0.38f, size.height), 200.dp.toPx(), 52.dp.toPx(), Color(0xFF352C50))
                drawCloud(Offset(size.width - 220.dp.toPx(), size.height), 300.dp.toPx(), 74.dp.toPx(), Color(0xFF2A2547))
            }
        }
        content()
    }
}

/** Stelle in frazioni dello schermo, quasi tutte in alto; raggio in dp (sopra 2 una stella a quattro punte). */
private val stars = listOf(
    Triple(0.12f, 0.06f, 1.2f), Triple(0.30f, 0.14f, 4.5f), Triple(0.52f, 0.05f, 1f),
    Triple(0.66f, 0.20f, 1.4f), Triple(0.90f, 0.27f, 3.5f), Triple(0.07f, 0.31f, 3f),
    Triple(0.42f, 0.36f, 1f), Triple(0.80f, 0.45f, 1.2f), Triple(0.20f, 0.55f, 1f),
)

/** Stella a quattro punte con i lati concavi, come i ✦ della finestra degli script. */
private fun sparkle(center: Offset, radius: Float): Path = Path().apply {
    val (cx, cy) = center
    val k = radius * 0.18f
    moveTo(cx, cy - radius)
    quadraticTo(cx + k, cy - k, cx + radius, cy)
    quadraticTo(cx + k, cy + k, cx, cy + radius)
    quadraticTo(cx - k, cy + k, cx - radius, cy)
    quadraticTo(cx - k, cy - k, cx, cy - radius)
    close()
}

/** La nuvola della finestra degli script (un tracciato 114×44), appoggiata con la base su [bottomLeft]. */
private fun DrawScope.drawCloud(bottomLeft: Offset, width: Float, height: Float, color: Color) {
    val sx = width / 114f
    val sy = height / 44f
    fun x(v: Float) = bottomLeft.x + v * sx
    fun y(v: Float) = bottomLeft.y - height + v * sy
    val path = Path().apply {
        moveTo(x(0f), y(40f))
        cubicTo(x(0f), y(28f), x(12f), y(22f), x(22f), y(26f))
        cubicTo(x(26f), y(10f), x(50f), y(6f), x(60f), y(20f))
        cubicTo(x(68f), y(8f), x(92f), y(10f), x(94f), y(28f))
        cubicTo(x(106f), y(26f), x(116f), y(34f), x(114f), y(44f))
        lineTo(x(0f), y(44f))
        close()
    }
    drawPath(path, color.copy(alpha = 0.9f))
}
