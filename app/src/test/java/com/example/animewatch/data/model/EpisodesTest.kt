package com.example.animewatch.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodesTest {

    private fun ep(season: Int, number: Int, airDate: String, runtime: Int = 24, name: String = "S${season}E$number") =
        TmdbEpisode(season, number, airDate, name, runtime, "/s${season}e$number.jpg", 8.0)

    /** Settimanale dal [start], [count] puntate a partire dalla [from] della stagione TMDB [season]. */
    private fun weekly(season: Int, from: Int, count: Int, start: String): List<TmdbEpisode> {
        val first = java.time.LocalDate.parse(start)
        return (0 until count).map { ep(season, from + it, first.plusWeeks(it.toLong()).toString()) }
    }

    // Come Frieren: S1 (28) e S2 (10) di AniList sono la "stagione 1" di TMDB, da 38 puntate; più uno speciale.
    private val frieren = TmdbShow(
        id = 209867,
        name = "Frieren",
        overview = "Trama della serie",
        firstAirDate = "2023-09-29",
        seasons = listOf(
            TmdbSeason(0, "Speciali", listOf(ep(0, 1, "2023-10-01", runtime = 3))),
            TmdbSeason(1, "Trama della stagione 1", weekly(1, 1, 28, "2023-09-29") + weekly(1, 29, 10, "2026-01-16")),
        ),
    )
    private val s1 = SeasonDates(1, "TV", "2023-09-29", "2024-04-05")
    private val s2 = SeasonDates(2, "TV", "2026-01-16", null)

    @Test
    fun `le stagioni AniList si ritagliano per data dalla stagione TMDB e si rinumerano da 1`() {
        val first = EpisodeMatch.episodes(s1, frieren)
        val second = EpisodeMatch.episodes(s2, frieren)
        assertEquals(28, first.size)
        assertEquals(10, second.size)
        assertEquals((1..10).toList(), second.map { it.number })
        assertEquals("S1E29", second.first().title)
        assertEquals(EpisodeMatch.IMAGE_BASE + "/s1e29.jpg", second.first().thumbnailUrl)
        assertEquals(24, second.first().length)
    }

    @Test
    fun `un giorno di differenza fra AniList e TMDB si tollera`() {
        val shifted = SeasonDates(2, "TV", "2026-01-17", "2026-03-20")
        assertEquals(10, EpisodeMatch.episodes(shifted, frieren).size)
    }

    @Test
    fun `OVA e speciali si cercano nella stagione 0, le serie TV mai`() {
        val special = SeasonDates(3, "SPECIAL", "2023-10-01", "2023-10-01")
        assertEquals(listOf(3), EpisodeMatch.episodes(special, frieren).map { it.length })
        assertTrue(EpisodeMatch.episodes(s1, frieren).none { it.length == 3 })
    }

    @Test
    fun `senza data di inizio completa non si abbina nulla`() {
        assertTrue(EpisodeMatch.episodes(SeasonDates(4, "TV", null, null), frieren).isEmpty())
    }

    @Test
    fun `titoli generici, voti e durate a zero diventano assenti`() {
        val show = frieren.copy(
            seasons = listOf(TmdbSeason(1, null, listOf(TmdbEpisode(1, 1, "2023-09-29", "Episodio 1", 0, "", 0.0)))),
        )
        val episode = EpisodeMatch.episodes(s1, show).single()
        assertNull(episode.title)
        assertNull(episode.thumbnailUrl)
        assertNull(episode.length)
        assertNull(episode.score)
    }

    @Test
    fun `la trama è quella della stagione TMDB che contiene le puntate, altrimenti quella della serie`() {
        // Anche la S2, che su TMDB sta nella stessa stagione della S1, prende la trama di quella stagione.
        assertEquals("Trama della stagione 1", EpisodeMatch.overview(s1, frieren))
        assertEquals("Trama della stagione 1", EpisodeMatch.overview(s2, frieren))
        val noSeasonOverview = frieren.copy(seasons = frieren.seasons.map { it.copy(overview = "") })
        assertEquals("Trama della serie", EpisodeMatch.overview(s2, noSeasonOverview))
        // Gli speciali stanno nella stagione 0, che non racconta la serie: si usa la trama della serie.
        assertEquals("Trama della serie", EpisodeMatch.overview(SeasonDates(3, "SPECIAL", "2023-10-01", "2023-10-01"), frieren))
        assertNull(EpisodeMatch.overview(s1, noSeasonOverview.copy(overview = null)))
    }

    @Test
    fun `la serie si accetta solo se ha le puntate della stagione cercata`() {
        assertTrue(EpisodeMatch.accepts(frieren, s1))
        assertFalse(EpisodeMatch.accepts(frieren, SeasonDates(1, "TV", "2010-01-01", "2010-03-01")))
    }

    @Test
    fun `fra i candidati si provano le animazioni vicine nel tempo`() {
        val results = listOf(
            TmdbCandidate(1, "2023-09-29", listOf(18)),
            TmdbCandidate(2, "2009-04-05", listOf(16)),
            TmdbCandidate(3, "2023-10-06", listOf(16, 10759)),
            TmdbCandidate(4, null, listOf(16)),
        )
        assertEquals(listOf(3), EpisodeMatch.candidates(results, s1))
    }

    @Test
    fun `le puntate in cache scadono prima per le serie in corso`() {
        val hour = 60L * 60 * 1000
        val entry = SeasonEpisodes(1, tmdbId = 10, overview = null, episodes = emptyList(), cachedAt = 0)
        val finished = Anime(
            id = 1, title = "A", titleEnglish = null, coverUrl = null, genres = emptyList(), episodes = 12,
            airedEpisodes = null, status = "FINISHED", season = null, seasonYear = null, averageScore = null,
            description = null,
        )
        assertFalse(entry.isStale(finished, now = 13 * hour))
        assertTrue(entry.isStale(finished.copy(status = "RELEASING"), now = 13 * hour))
        assertTrue(entry.isStale(null, now = 13 * hour))
        assertTrue(entry.isStale(finished, now = 31 * 24 * hour))
    }
}
