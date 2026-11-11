package com.example.animewatch.data.model

/** Puntata con i dati di TMDB. Il numero parte da 1 in ogni stagione di AniList, come nel progresso. */
data class Episode(
    val number: Int,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    /** Minuti. */
    val length: Int? = null,
    /** Voto degli utenti di TMDB, da 0 a 10; null se nessuno ha votato. */
    val score: Double? = null,
)

/**
 * Le puntate di una stagione AniList e la sua trama in italiano, come documento della cache condivisa
 * `episodes/{animeId}`. Si salva anche vuoto: TMDB non ha tutte le serie, e non va richiesto a ogni apertura.
 */
data class SeasonEpisodes(
    val animeId: Int,
    /** La serie TMDB del franchise; null se non è stata trovata. Evita di ripetere la ricerca. */
    val tmdbId: Int?,
    val overview: String?,
    val episodes: List<Episode>,
    val cachedAt: Long = System.currentTimeMillis(),
) {
    /** Le serie in corso aggiungono puntate; le concluse cambiano di rado. Senza scheda vale come in corso. */
    fun isStale(anime: Anime?, now: Long = System.currentTimeMillis()): Boolean =
        now - cachedAt > if (anime?.isFinished == true) 30L * DAY_MS else DAY_MS / 2

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** Stagione AniList con le date di uscita (`yyyy-MM-dd`), per cercarne le puntate su TMDB. */
data class SeasonDates(
    val animeId: Int,
    val format: String?,
    val start: String?,
    /** null per le serie in corso. */
    val end: String?,
)

data class TmdbEpisode(
    val season: Int,
    val number: Int,
    val airDate: String?,
    val name: String?,
    val runtime: Int?,
    val still: String?,
    val vote: Double?,
)

data class TmdbSeason(val number: Int, val overview: String?, val episodes: List<TmdbEpisode>)

data class TmdbShow(
    val id: Int,
    val name: String?,
    val overview: String?,
    val firstAirDate: String?,
    val seasons: List<TmdbSeason>,
)

data class TmdbCandidate(val id: Int, val firstAirDate: String?, val genreIds: List<Int>)

/**
 * Abbinamento fra le stagioni di AniList e le puntate di TMDB. TMDB divide le serie a modo suo (Frieren S1 e S2 sono
 * un'unica "stagione 1" da 38 puntate, Fire Force S3 Part 2 è la S3 dalla 13), mentre AniList sa quando è uscita
 * ogni sua stagione: le puntate di una stagione sono quelle uscite fra il suo inizio e la sua fine, rinumerate da 1.
 */
object EpisodeMatch {
    const val IMAGE_BASE = "https://image.tmdb.org/t/p/w300"

    /** Formati che su TMDB stanno nelle stagioni normali; gli altri (OVA, speciali) nella stagione 0. */
    private val REGULAR = setOf("TV", "TV_SHORT", "ONA")

    /** TMDB e AniList possono differire di un giorno (fuso orario della prima messa in onda). */
    private const val TOLERANCE_DAYS = 1L

    fun episodes(season: SeasonDates, show: TmdbShow): List<Episode> =
        matched(season, show).mapIndexed { index, e ->
            Episode(
                number = index + 1,
                title = e.name?.trim()?.takeIf { it.isNotEmpty() && !GENERIC_TITLE.matches(it) },
                thumbnailUrl = e.still?.takeIf { it.isNotBlank() }?.let { IMAGE_BASE + it },
                length = e.runtime?.takeIf { it > 0 },
                score = e.vote?.takeIf { it > 0 },
            )
        }

    /**
     * Trama in italiano della stagione: quella della stagione TMDB che ne contiene le puntate (anche le "Part 2", che
     * su TMDB stanno nella stessa stagione della prima parte), altrimenti quella della serie. TMDB non ha sempre una
     * trama per stagione: Frieren ha solo la "stagione 1", senza trama, e quindi anche la S2 mostra quella della serie.
     */
    fun overview(season: SeasonDates, show: TmdbShow): String? {
        val tmdbSeason = matched(season, show).firstOrNull()?.season?.takeIf { it > 0 }
        return tmdbSeason?.let { n -> show.seasons.find { it.number == n }?.overview }?.takeIf { it.isNotBlank() }
            ?: show.overview?.takeIf { it.isNotBlank() }
    }

    private fun matched(season: SeasonDates, show: TmdbShow): List<TmdbEpisode> {
        val start = season.start?.let(::day) ?: return emptyList()
        val end = season.end?.let(::day) ?: Long.MAX_VALUE / 2
        val special = season.format !in REGULAR
        return show.seasons.asSequence()
            .filter { (it.number == 0) == special }
            .flatMap { it.episodes }
            .filter { e ->
                val aired = e.airDate?.let(::day) ?: return@filter false
                aired >= start - TOLERANCE_DAYS && aired <= end + TOLERANCE_DAYS
            }
            .sortedWith(compareBy({ it.airDate }, { it.season }, { it.number }))
            .toList()
    }

    /**
     * Candidati della ricerca da provare, al massimo [limit]: prima le animazioni (genere 16) uscite vicino alla
     * stagione [anchor], poi le altre animazioni. Chi è troppo lontano nel tempo non si prova.
     */
    fun candidates(results: List<TmdbCandidate>, anchor: SeasonDates, limit: Int = 2): List<Int> {
        val start = anchor.start?.let(::day)
        return results
            .filter { 16 in it.genreIds }
            .map { it to (start?.let { s -> it.firstAirDate?.let(::day)?.let { d -> kotlin.math.abs(d - s) } } ?: Long.MAX_VALUE) }
            .filter { (_, distance) -> distance <= MAX_DISTANCE_DAYS || start == null }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first.id }
    }

    /** La serie va bene se ha le puntate della stagione da cui si è cercato. */
    fun accepts(show: TmdbShow, anchor: SeasonDates): Boolean = matched(anchor, show).isNotEmpty()

    /** TMDB mette "Episodio 12" quando il titolo non è ancora noto: meglio "Puntata 12" dell'app. */
    private val GENERIC_TITLE = Regex("""^(Episodio|Episode)\s+\d+$""", RegexOption.IGNORE_CASE)

    /** Una stagione che inizia anni dopo la serie TMDB (un remake con lo stesso nome) non è quella. */
    private const val MAX_DISTANCE_DAYS = 366L

    /** Giorni dal 1970 di una data `yyyy-MM-dd`; null se non è completa. */
    private fun day(date: String): Long? = runCatching { java.time.LocalDate.parse(date).toEpochDay() }.getOrNull()
}
