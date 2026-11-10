package com.example.animewatch.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.WatchProgress
import com.example.animewatch.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthState {
    /** Firebase non ha ancora detto se c'è una sessione salvata: dura una frazione di secondo all'avvio. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String) : AuthState
}

/**
 * Stato condiviso da tutte le schermate: utente, liste, progressi e schede anime in memoria.
 * Liste e progressi arrivano da Firestore come flussi in tempo reale (anche offline, dalla cache
 * locale), quindi la UI non deve mai "ricaricare" dopo una modifica.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val authState: StateFlow<AuthState> = container.auth.currentUid
        .map { if (it == null) AuthState.SignedOut else AuthState.SignedIn(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val uid: StateFlow<String?> = authState
        .map { (it as? AuthState.SignedIn)?.uid }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Il catch sta dentro flatMapLatest: un errore chiude solo il flusso di quell'utente, non quello
    // esterno, così dopo un nuovo accesso la sincronizzazione riparte.
    val lists: StateFlow<List<AnimeList>> = uid
        .flatMapLatest { u ->
            if (u == null) {
                flowOf(emptyList())
            } else {
                container.userData.observeLists(u).catch {
                    reportSyncError(u, "Sincronizzazione delle liste non riuscita: ${it.message}")
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progress: StateFlow<Map<Int, WatchProgress>> = uid
        .flatMapLatest { u ->
            if (u == null) {
                flowOf(emptyMap())
            } else {
                container.userData.observeProgress(u).catch {
                    reportSyncError(u, "Sincronizzazione dei progressi non riuscita: ${it.message}")
                    emit(emptyMap())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _anime = MutableStateFlow<Map<Int, Anime>>(emptyMap())
    val anime: StateFlow<Map<Int, Anime>> = _anime.asStateFlow()

    /** All'uscita i listener del vecchio utente possono fallire per permessi: quell'errore non va mostrato. */
    private fun reportSyncError(forUid: String, message: String) {
        if (uid.value == forUid) _error.value = message
    }

    // ---------- Accesso ----------

    /** Il context dell'Activity serve solo per mostrare il selettore degli account, non viene conservato. */
    fun signInWithGoogle(activityContext: Context) {
        if (_signingIn.value) return
        viewModelScope.launch {
            _signingIn.value = true
            _loginError.value = null
            try {
                container.auth.signInWithGoogle(activityContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loginError.value = "Accesso non riuscito: ${e.message}"
            } finally {
                _signingIn.value = false
            }
        }
    }

    fun signOut() {
        _error.value = null
        viewModelScope.launch { container.auth.signOut() }
    }

    fun dismissError() {
        _error.value = null
    }

    /** Carica (cache -> Firestore -> AniList) le schede non ancora in memoria. */
    fun ensureAnime(ids: Collection<Int>) {
        val missing = ids.filter { it !in _anime.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = container.animeRepo.getMany(missing)
            _anime.update { it + loaded }
        }
    }

    // ---------- Liste ----------

    fun createList(name: String) {
        val u = uid.value ?: return
        val clean = name.trim()
        if (clean.isNotEmpty()) container.userData.createList(u, clean)
    }

    fun deleteList(listId: String) {
        val u = uid.value ?: return
        container.userData.deleteList(u, listId)
    }

    fun addToList(listId: String, anime: Anime) {
        val u = uid.value ?: return
        container.animeRepo.cache(listOf(anime)) // la scheda entra nella cache condivisa
        container.userData.addToList(u, listId, anime.id)
        _anime.update { it + (anime.id to anime) }
    }

    fun removeFromList(listId: String, animeId: Int) {
        val u = uid.value ?: return
        container.userData.removeFromList(u, listId, animeId)
    }

    // ---------- Puntate e stagioni ----------

    fun toggleEpisode(anime: Anime, episode: Int) {
        val current = progress.value[anime.id]?.watchedEpisodes.orEmpty()
        save(anime, if (episode in current) current - episode else current + episode)
    }

    /** "Stagione vista": segna tutte le puntate disponibili. */
    fun markAllWatched(anime: Anime) = save(anime, (1..anime.availableEpisodes).toSet())

    fun clearProgress(anime: Anime) = save(anime, emptySet())

    private fun save(anime: Anime, watched: Set<Int>) {
        val u = uid.value ?: return
        container.userData.saveProgress(
            u,
            WatchProgress(
                animeId = anime.id,
                watchedEpisodes = watched,
                genres = anime.genres,
                totalEpisodes = anime.availableEpisodes,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
