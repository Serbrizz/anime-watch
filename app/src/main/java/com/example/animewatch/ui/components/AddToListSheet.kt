@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.animewatch.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.Franchise
import com.example.animewatch.ui.AppViewModel
import com.example.animewatch.ui.theme.Night
import kotlinx.coroutines.launch

/**
 * Pannello dal basso per mettere un anime nelle liste o toglierlo. Vale per tutta la serie: se una qualunque stagione
 * è già nella lista, la lista risulta spuntata. In fondo si crea una lista nuova che contiene già l'anime.
 */
@Composable
fun AddToListSheet(vm: AppViewModel, anime: Anime, onDismiss: () -> Unit) {
    val lists by vm.editableLists.collectAsStateWithLifecycle()
    val franchises by vm.franchises.collectAsStateWithLifecycle()
    LaunchedEffect(anime.id) { vm.ensureFranchises(listOf(anime.id)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // "Fatto" fa scendere il pannello con la sua animazione prima di toglierlo dalla composizione.
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { SheetHeader(anime) }
            if (lists.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Non hai ancora liste: creane una qui sotto e l'anime ci finisce dentro subito.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(lists, key = { it.id }) { list ->
                ListChoice(
                    list = list,
                    count = Franchise.groupList(list.animeIds, franchises).size,
                    selected = list.containsSeries(anime.id, franchises),
                    onToggle = { add ->
                        if (add) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.setInList(list, anime, add)
                    },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "create") {
                NewListField(
                    onCreate = { name ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.createListWith(name, anime)
                    },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "done") {
                InkButton(onClick = close, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Fatto") }
            }
        }
    }
}

@Composable
private fun SheetHeader(anime: Anime) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        CoverImage(
            anime.coverUrl,
            Modifier
                .rotate(-4f)
                .shadow(6.dp, RoundedCornerShape(8.dp))
                .size(width = 48.dp, height = 68.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                "Aggiungi a una lista",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                anime.englishOrRomaji,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Una lista come scheda da accendere e spegnere, come le opzioni della finestra degli script: spenta è rialzata col
 * bordo sottile, accesa arancione piena col contorno a china. Colore e spunta cambiano con un piccolo rimbalzo.
 */
@Composable
private fun ListChoice(
    list: AnimeList,
    count: Int,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(if (selected) Night.Accent else Night.Raised, label = "sfondo lista")
    val content by animateColorAsState(if (selected) Night.AccentInk else Night.Text, label = "testo lista")
    Surface(
        checked = selected,
        onCheckedChange = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        border = if (selected) BorderStroke(2.dp, Night.Ink) else BorderStroke(1.5.dp, Night.Line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (list.isShared) "$count anime · Condivisa" else "$count anime",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(12.dp))
            // Sull'arancione la spunta prende il colore del testo, altrimenti sparirebbe.
            InListIcon(
                selected,
                addDescription = null,
                removeDescription = null,
                Modifier.size(28.dp),
                addedTint = content,
            )
        }
    }
}

/** "+" che diventa una spunta con un piccolo rimbalzo quando l'anime entra nella lista (e torna "+" se esce). */
@Composable
fun InListIcon(
    inList: Boolean,
    addDescription: String?,
    removeDescription: String?,
    modifier: Modifier = Modifier,
    addedTint: Color = MaterialTheme.colorScheme.primary,
) {
    AnimatedContent(
        targetState = inList,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                (scaleOut() + fadeOut())
        },
        label = "spunta lista",
    ) { added ->
        if (added) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = removeDescription,
                tint = addedTint,
                modifier = modifier,
            )
        } else {
            Icon(
                Icons.Default.Add,
                contentDescription = addDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
            )
        }
    }
}

/** "Nuova lista": il pulsante si apre in un campo per il nome; confermando la lista nasce con l'anime dentro. */
@Composable
private fun NewListField(onCreate: (String) -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val submit: () -> Unit = {
        if (name.isNotBlank()) {
            onCreate(name)
            name = ""
            editing = false
        }
    }

    AnimatedContent(
        targetState = editing,
        transitionSpec = { (fadeIn() + expandVertically()) togetherWith fadeOut() },
        label = "nuova lista",
        modifier = modifier,
    ) { isEditing ->
        if (!isEditing) {
            Surface(
                onClick = { editing = true },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Nuova lista con questo anime",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome della lista") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                )
                Spacer(Modifier.width(8.dp))
                InkButton(onClick = submit, enabled = name.isNotBlank(), contentPadding = PaddingValues(12.dp)) {
                    Icon(Icons.Default.Check, contentDescription = "Crea la lista")
                }
            }
        }
    }
}
