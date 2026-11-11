package com.example.animewatch.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry

/** Titolo e azioni che una schermata mette nella barra in alto comune (disegnata da `AnimeWatchRoot`). */
data class ScreenBarSpec(val title: String, val actions: @Composable RowScope.() -> Unit)

/**
 * Le barre dichiarate dalle schermate, per voce del back stack. La chiave è la voce e non la rotta, così durante
 * l'animazione fra due schermate (entrambe composte) ognuna scrive solo la propria e la barra legge quella corrente.
 */
@Stable
class ScreenBars {
    private val specs = mutableStateMapOf<String, ScreenBarSpec>()

    operator fun get(entry: NavBackStackEntry?): ScreenBarSpec? = entry?.let { specs[it.id] }

    internal fun set(id: String, spec: ScreenBarSpec) {
        specs[id] = spec
    }

    internal fun remove(id: String) {
        specs.remove(id)
    }
}

val LocalScreenBars = staticCompositionLocalOf { ScreenBars() }

/**
 * Titolo e azioni della schermata nella barra comune. Freccia indietro e accesso al Profilo li aggiunge la barra da
 * sola, quindi una schermata non deve occuparsene; una sezione nuova della barra in basso può anche non chiamarla
 * (il titolo diventa l'etichetta della sezione).
 */
@Composable
fun ScreenBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val bars = LocalScreenBars.current
    // Dentro il NavHost il proprietario dei ViewModel è la voce del back stack della schermata.
    val id = (LocalViewModelStoreOwner.current as? NavBackStackEntry)?.id ?: return
    val spec = ScreenBarSpec(title, actions)
    SideEffect { bars.set(id, spec) }
    DisposableEffect(id) { onDispose { bars.remove(id) } }
}
