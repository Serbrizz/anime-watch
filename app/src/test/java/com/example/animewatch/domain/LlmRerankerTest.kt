package com.example.animewatch.domain

import com.example.animewatch.data.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmRerankerTest {

    private fun progress(id: Int, watched: Int, total: Int, updatedAt: Long) = WatchProgress(
        animeId = id, watchedEpisodes = (1..watched).toSet(), totalEpisodes = total, updatedAt = updatedAt,
    )

    private val titles = mapOf(1 to "Frieren", 2 to "Bleach", 3 to "Gintama", 4 to "Sconosciuto")

    @Test
    fun `visti dai piu recenti, con quanto se ne e visto`() {
        val lines = LlmReranker.describeWatched(
            listOf(
                progress(1, watched = 28, total = 28, updatedAt = 10),
                progress(2, watched = 12, total = 366, updatedAt = 30),
                progress(3, watched = 5, total = 0, updatedAt = 20),
            ),
        ) { titles[it] }
        assertEquals(listOf("Bleach (12/366)", "Gintama (5 puntate)", "Frieren (tutto)"), lines)
    }

    @Test
    fun `scarta chi non ha titolo o puntate viste e si ferma a 200`() {
        val many = (100 until 320).map { progress(it, watched = 1, total = 12, updatedAt = it.toLong()) }
        val lines = LlmReranker.describeWatched(
            many + progress(4, watched = 0, total = 12, updatedAt = 999) + progress(5, watched = 3, total = 12, updatedAt = 998),
        ) { if (it == 5) null else titles[it] ?: "Anime $it" }
        assertEquals(200, lines.size)
        assertEquals("Anime 319 (1/12)", lines.first())
    }
}
