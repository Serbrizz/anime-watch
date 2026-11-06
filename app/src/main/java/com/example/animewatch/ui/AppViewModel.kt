package com.example.animewatch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.WatchProgress
import com.example.animewatch.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stato condiviso da tutte le schermate: liste, progressi e schede anime in memoria.
 * Liste e progressi arrivano dallo [com.example.animewatch.data.UserStore] come flussi: la UI
 * si aggiorna da sola dopo ogni modifica, senza "ricaricare".
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val lists: StateFlow<List<AnimeList>> = container.userStore.observeLists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progress: StateFlow<Map<Int, WatchProgress>> = container.userStore.observeProgress()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _anime = MutableStateFlow<Map<Int, Anime>>(emptyMap())
    val anime: StateFlow<Map<Int, Anime>> = _anime.asStateFlow()

    /** Carica (memoria -> store -> AniList) le schede non ancora in memoria. */
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
        val clean = name.trim()
        if (clean.isNotEmpty()) container.userStore.createList(clean)
    }

    fun deleteList(listId: String) = container.userStore.deleteList(listId)

    fun addToList(listId: String, anime: Anime) {
        container.animeRepo.cache(listOf(anime)) // garantisce che la scheda sia nello store
        container.userStore.addToList(listId, anime.id)
        _anime.update { it + (anime.id to anime) }
    }

    fun removeFromList(listId: String, animeId: Int) =
        container.userStore.removeFromList(listId, animeId)

    // ---------- Puntate e stagioni ----------

    fun toggleEpisode(anime: Anime, episode: Int) {
        val current = progress.value[anime.id]?.watchedEpisodes.orEmpty()
        save(anime, if (episode in current) current - episode else current + episode)
    }

    /** "Stagione vista": segna tutte le puntate disponibili. */
    fun markAllWatched(anime: Anime) = save(anime, (1..anime.availableEpisodes).toSet())

    fun clearProgress(anime: Anime) = save(anime, emptySet())

    private fun save(anime: Anime, watched: Set<Int>) {
        container.userStore.saveProgress(
            WatchProgress(
                animeId = anime.id,
                watchedEpisodes = watched,
                genres = anime.genres,
                totalEpisodes = anime.availableEpisodes,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
