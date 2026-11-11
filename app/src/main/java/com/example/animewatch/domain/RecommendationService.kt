package com.example.animewatch.domain

import com.example.animewatch.data.AnimeRepository
import com.example.animewatch.data.model.WatchProgress
import com.example.animewatch.data.remote.anilist.AniListClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class RecommendationResult(
    val items: List<Recommendation>,
    /** true se l'ordine e le motivazioni arrivano da OpenRouter. */
    val llmUsed: Boolean,
)

/** Orchestrazione: profilo -> candidati da AniList -> ranking -> (opzionale) riordino AI. */
class RecommendationService(
    private val aniList: AniListClient,
    private val animeRepo: AnimeRepository,
    private val reranker: LlmReranker,
) {
    val llmAvailable: Boolean get() = reranker.isAvailable

    suspend fun recommend(
        progress: Collection<WatchProgress>,
        watchedTitles: List<String>,
        excludeIds: Set<Int>,
        useLlm: Boolean,
    ): RecommendationResult {
        val profile = RecommendationEngine.buildProfile(progress)
        val topGenres = RecommendationEngine.topGenres(profile, TOP_GENRES)
        if (topGenres.isEmpty()) return RecommendationResult(emptyList(), false)

        // Una richiesta per genere: indipendente da come AniList combina più generi (AND/OR).
        // Intanto si ricostruiscono le serie che l'utente conosce già (di solito già in memoria: le usano le liste).
        val (results, knownSeasons) = coroutineScope {
            val known = async { knownSeasons(excludeIds) }
            val genres = topGenres.map { genre -> async { runCatching { aniList.popularByGenre(genre) } } }
            genres.awaitAll() to known.await()
        }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        val candidates = results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }
        animeRepo.remember(candidates)

        // Via ogni stagione delle serie già note (in lista o iniziate): il seguito di ciò che si è visto non è un consiglio.
        val ranked = RecommendationEngine.rank(candidates, profile, excludeIds + knownSeasons)
        val series = RecommendationEngine.onePerSeries(ranked)

        if (useLlm && reranker.isAvailable && series.isNotEmpty()) {
            reranker.rerank(topGenres, watchedTitles, series.take(POOL_FOR_LLM), LLM_RESULTS)
                ?.let { return RecommendationResult(it, true) }
        }
        return RecommendationResult(series.take(RESULTS), false)
    }

    /** Tutte le stagioni delle serie indicate. Senza rete le catene restano incomplete, ma i consigli si mostrano lo stesso. */
    private suspend fun knownSeasons(ids: Set<Int>): Set<Int> =
        runCatching { animeRepo.getWithRelated(ids).keys }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(emptySet())

    private companion object {
        const val TOP_GENRES = 3
        const val POOL_FOR_LLM = 30
        const val LLM_RESULTS = 12
        const val RESULTS = 20
    }
}
