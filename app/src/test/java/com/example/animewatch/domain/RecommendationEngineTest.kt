package com.example.animewatch.domain

import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    private fun anime(id: Int, genres: List<String>, score: Int? = 70) = Anime(
        id = id, title = "Anime $id", titleEnglish = null, coverUrl = null, genres = genres,
        episodes = 12, airedEpisodes = null, status = "FINISHED", season = null,
        seasonYear = null, averageScore = score, description = null,
    )

    private fun progress(id: Int, watched: Int, total: Int, genres: List<String>) = WatchProgress(
        animeId = id, watchedEpisodes = (1..watched).toSet(), genres = genres, totalEpisodes = total,
    )

    private val profile = RecommendationEngine.buildProfile(
        listOf(
            progress(1, 12, 12, listOf("Action", "Fantasy")),
            progress(2, 12, 12, listOf("Action", "Drama")),
            progress(3, 3, 12, listOf("Romance")),
        )
    )

    @Test
    fun `il genere piu guardato ha peso massimo`() {
        assertEquals(1.0, profile.getValue("Action"), 1e-9)
        assertTrue(profile.getValue("Romance") < profile.getValue("Fantasy"))
    }

    @Test
    fun `topGenres ordina per peso`() {
        assertEquals("Action", RecommendationEngine.topGenres(profile, 3).first())
    }

    @Test
    fun `senza puntate viste il profilo e vuoto`() {
        val empty = RecommendationEngine.buildProfile(listOf(WatchProgress(animeId = 9, genres = listOf("Action"))))
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `il candidato coi generi preferiti batte quello con un genere marginale`() {
        val ranked = RecommendationEngine.rank(
            candidates = listOf(anime(10, listOf("Romance")), anime(11, listOf("Action", "Fantasy"))),
            profile = profile,
            excludeIds = emptySet(),
        )
        assertEquals(listOf(11, 10), ranked.map { it.anime.id })
    }

    @Test
    fun `esclude gia visti e candidati senza generi in comune`() {
        val ranked = RecommendationEngine.rank(
            candidates = listOf(anime(20, listOf("Horror")), anime(21, listOf("Action")), anime(22, listOf("Action"))),
            profile = profile,
            excludeIds = setOf(21),
        )
        assertEquals(listOf(22), ranked.map { it.anime.id })
    }
}
