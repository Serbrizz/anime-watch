package com.example.animewatch.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewatch.data.AnimeRepository
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.Franchise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [franchises]: i franchise già risolti (quelli delle liste), per riconoscere come una serie sola anche le
 *  stagioni che fra i risultati non sono collegate direttamente. */
class SearchViewModel(
    private val repo: AnimeRepository,
    franchises: StateFlow<Map<Int, Franchise>>,
) : ViewModel() {

    data class State(
        val query: String = "",
        val loading: Boolean = false,
        /** Così come arrivano da AniList (già dal più famoso), una scheda per stagione. */
        val results: List<Anime> = emptyList(),
        val searched: Boolean = false,
        val error: String? = null,
        /** Il testo davvero cercato, se la ricerca esatta non ha trovato nulla e si è riprovato con meno parole. */
        val fallbackQuery: String? = null,
    )

    private val _state = MutableStateFlow(State())
    /** Resta un flusso diretto: il testo del campo passa da qui e non deve arrivare in ritardo. */
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Una riga per serie, con la stagione più famosa fra quelle trovate. Di solito è la S1; non si mostra la
     * prima uscita perché a volte è un'altra cosa: per "lelouch" sarebbe il primo film riassuntivo e il film
     * cercato, Re;surrection (suo seguito su AniList), sparirebbe dentro quella riga.
     */
    val series: StateFlow<List<Anime>> = combine(_state, franchises) { state, known ->
        Franchise.collapse(state.results, known) { it }.map { it.first() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var job: Job? = null

    /** Debounce di 500 ms: si cerca quando l'utente smette di scrivere, non a ogni lettera
     *  (AniList limita a ~90 richieste/min). */
    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text) }
        job?.cancel()
        if (text.trim().length < 2) {
            _state.update {
                it.copy(results = emptyList(), loading = false, searched = false, error = null, fallbackQuery = null)
            }
            return
        }
        job = viewModelScope.launch {
            delay(500)
            _state.update { it.copy(loading = true, error = null) }
            try {
                val query = text.trim()
                var results = repo.search(query)
                // AniList confronta parole intere: un'ultima parola scritta a metà ("attack on tit") o diversa
                // dal titolo ("Resurrection" contro "Re;surrection") azzera tutto. Un solo tentativo in più.
                val shorter = withoutLastWord(query)
                val fallback = if (results.isEmpty() && shorter != null) shorter else null
                if (fallback != null) results = repo.search(fallback)
                _state.update {
                    it.copy(
                        loading = false,
                        results = results,
                        searched = true,
                        fallbackQuery = fallback?.takeIf { results.isNotEmpty() },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Ricerca non riuscita: ${e.message}") }
            }
        }
    }

    /** Il testo senza l'ultima parola, se ne restano almeno due lettere. */
    private fun withoutLastWord(query: String): String? =
        query.substringBeforeLast(' ', "").trim().takeIf { it.length >= 2 }
}
