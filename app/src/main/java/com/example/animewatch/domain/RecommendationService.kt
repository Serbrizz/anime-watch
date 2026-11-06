package com.example.animewatch.domain

import com.example.animewatch.data.AnimeRepository
import com.example.animewatch.data.model.WatchProgress
import com.example.animewatch.data.remote.anilist.AniListClient
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
        val results = coroutineScope {
            topGenres.map { genre -> async { runCatching { aniList.popularByGenre(genre) } } }.awaitAll()
        }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        val candidates = results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }
        animeRepo.cache(candidates)

        val ranked = RecommendationEngine.rank(candidates, profile, excludeIds)

        if (useLlm && reranker.isAvailable && ranked.isNotEmpty()) {
            reranker.rerank(topGenres, watchedTitles, ranked.take(POOL_FOR_LLM), LLM_RESULTS)
                ?.let { return RecommendationResult(it, true) }
        }
        return RecommendationResult(ranked.take(RESULTS), false)
    }

    private companion object {
        const val TOP_GENRES = 3
        const val POOL_FOR_LLM = 30
        const val LLM_RESULTS = 12
        const val RESULTS = 20
    }
}
