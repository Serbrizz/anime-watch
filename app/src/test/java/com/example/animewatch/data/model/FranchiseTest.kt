package com.example.animewatch.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FranchiseTest {

    private fun anime(
        id: Int,
        year: Int?,
        season: String? = null,
        format: String = "TV",
        episodes: Int = 12,
        prequels: List<Int> = emptyList(),
        sequels: List<Int> = emptyList(),
    ) = Anime(
        id = id, title = "Anime $id", titleEnglish = null, coverUrl = null, genres = emptyList(),
        episodes = episodes, airedEpisodes = null, status = "FINISHED", season = season, seasonYear = year,
        averageScore = null, description = null, format = format, prequelIds = prequels, sequelIds = sequels,
    )

    // S1 -> film -> S2 (TV) -> S3, con la relazione inversa mancante fra film e S2, come capita su AniList.
    private val s1 = anime(10, 2019, "SPRING", sequels = listOf(20))
    private val film = anime(20, 2020, "FALL", format = "MOVIE", episodes = 1, prequels = listOf(10))
    private val s2 = anime(30, 2021, "FALL", prequels = listOf(20), sequels = listOf(40))
    private val s3 = anime(40, 2021, "WINTER", prequels = listOf(30))
    private val other = anime(99, 2015)

    private val all = listOf(s3, other, film, s2, s1).associateBy { it.id }

    @Test
    fun `stagioni collegate anche in una sola direzione formano un franchise ordinato per uscita`() {
        val groups = Franchise.group(all)
        val expected = Franchise(rootId = 10, seasonIds = listOf(10, 20, 40, 30))
        assertEquals(expected, groups[10])
        assertEquals(expected, groups[30])
        assertEquals(Franchise.single(99), groups[99])
    }

    @Test
    fun `collapse unisce le stagioni anche attraverso una scheda assente e mantiene l'ordine`() {
        // Senza il film: S1 lo cita come sequel e S2 come prequel, quindi si ritrovano lo stesso.
        val groups = Franchise.collapse(listOf(s2, other, s1, s3)) { it }
        assertEquals(listOf(listOf(s2, s1, s3), listOf(other)), groups)
    }

    @Test
    fun `collapse usa i franchise gia risolti per le stagioni non collegate fra i risultati`() {
        // S1 e S3 da sole non si toccano (manca la S2, che solo S3 cita), ma il franchise noto le unisce.
        val results = listOf(s3, s1)
        assertEquals(2, Franchise.collapse(results) { it }.size)
        val known = Franchise.group(all)
        assertEquals(listOf(listOf(s3, s1)), Franchise.collapse(results, known) { it })
    }

    @Test
    fun `una lista con piu stagioni della stessa serie mostra una riga sola`() {
        val known = Franchise.group(all)
        val rows = Franchise.groupList(listOf(30, 99, 10, 77), known)
        assertEquals(listOf(10, 99, 77), rows.map { it.rootId })
    }

    @Test
    fun `etichette numerano le stagioni e i film solo se piu d'uno`() {
        val seasons = listOf(s1, film, s2, anime(50, 2022, format = "MOVIE"), anime(60, 2023, format = "OVA"))
        assertEquals(listOf("S1", "Film 1", "S2", "Film 2", "OVA"), Franchise.seasonLabels(seasons))
    }

    @Test
    fun `stagione corrente`() {
        val seasons = listOf(s1, film, s2)
        fun current(watched: Map<Int, Int>) = Franchise.currentSeasonIndex(seasons) { watched[it] ?: 0 }

        assertEquals("niente visto: la prima", 0, current(emptyMap()))
        assertEquals("prima a meta", 0, current(mapOf(10 to 5)))
        assertEquals("prima finita: la successiva", 1, current(mapOf(10 to 12)))
        assertEquals("film saltato, S2 iniziata", 2, current(mapOf(10 to 12, 30 to 3)))
        assertEquals("tutto visto: l'ultima", 2, current(mapOf(10 to 12, 20 to 1, 30 to 12)))
    }
}
