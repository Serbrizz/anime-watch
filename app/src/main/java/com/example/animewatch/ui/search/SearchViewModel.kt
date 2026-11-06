package com.example.animewatch.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewatch.data.AnimeRepository
import com.example.animewatch.data.model.Anime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: AnimeRepository) : ViewModel() {

    data class State(
        val query: String = "",
        val loading: Boolean = false,
        val results: List<Anime> = emptyList(),
        val searched: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    /** Debounce di 500 ms: si cerca quando l'utente smette di scrivere, non a ogni lettera
     *  (AniList limita a ~90 richieste/min). */
    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text) }
        job?.cancel()
        if (text.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), loading = false, searched = false, error = null) }
            return
        }
        job = viewModelScope.launch {
            delay(500)
            _state.update { it.copy(loading = true, error = null) }
            try {
                val results = repo.search(text.trim())
                _state.update { it.copy(loading = false, results = results, searched = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Ricerca non riuscita: ${e.message}") }
            }
        }
    }
}
