package com.example.animewatch.ui.recs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewatch.data.model.WatchProgress
import com.example.animewatch.domain.Recommendation
import com.example.animewatch.domain.RecommendationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecommendationsViewModel(private val service: RecommendationService) : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val items: List<Recommendation> = emptyList(),
        val llmUsed: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    val llmAvailable: Boolean get() = service.llmAvailable

    enum class AiStatus { CHECKING, AVAILABLE, UNAVAILABLE }

    /** Esito del preflight sul proxy: l'interruttore AI è attivo solo con AVAILABLE. */
    private val _aiStatus = MutableStateFlow(AiStatus.CHECKING)
    val aiStatus: StateFlow<AiStatus> = _aiStatus.asStateFlow()
    private var aiJob: Job? = null

    /** All'apertura: se l'AI risulta già disponibile non serve richiederlo (un errore la segna comunque non disponibile). */
    fun checkAiIfNeeded() {
        if (_aiStatus.value != AiStatus.AVAILABLE) checkAi()
    }

    fun checkAi() {
        if (!service.llmAvailable || aiJob?.isActive == true) return
        aiJob = viewModelScope.launch {
            _aiStatus.value = AiStatus.CHECKING
            _aiStatus.value = if (service.llmReachable()) AiStatus.AVAILABLE else AiStatus.UNAVAILABLE
        }
    }

    /** [wantsLlm] è l'interruttore: l'AI si usa solo se anche il preflight dice di sì. */
    fun load(
        progress: Collection<WatchProgress>,
        watchedTitles: List<String>,
        excludeIds: Set<Int>,
        wantsLlm: Boolean,
        force: Boolean = false,
    ) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                // Se il preflight è in corso (apertura, "Aggiorna") se ne attende l'esito invece di partire senza AI.
                if (wantsLlm) aiJob?.join()
                val useLlm = wantsLlm && _aiStatus.value == AiStatus.AVAILABLE
                val result = service.recommend(progress, watchedTitles, excludeIds, useLlm, force)
                _state.update { it.copy(loading = false, items = result.items, llmUsed = result.llmUsed) }
                // Il preflight può dire sì e la chiamata fallire comunque (modelli saturi, limite per utente).
                if (useLlm && !result.llmUsed && result.items.isNotEmpty()) _aiStatus.value = AiStatus.UNAVAILABLE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Impossibile caricare i consigli: ${e.message}") }
            }
        }
    }
}
