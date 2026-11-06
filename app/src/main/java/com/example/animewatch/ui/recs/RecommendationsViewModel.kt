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

    fun load(
        progress: Collection<WatchProgress>,
        watchedTitles: List<String>,
        excludeIds: Set<Int>,
        useLlm: Boolean,
    ) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val result = service.recommend(progress, watchedTitles, excludeIds, useLlm)
                _state.update { it.copy(loading = false, items = result.items, llmUsed = result.llmUsed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Impossibile caricare i consigli: ${e.message}") }
            }
        }
    }
}
