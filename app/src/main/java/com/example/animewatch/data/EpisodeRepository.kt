package com.example.animewatch.data

import com.example.animewatch.data.firebase.toMap
import com.example.animewatch.data.firebase.toSeasonEpisodes
import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.EpisodeMatch
import com.example.animewatch.data.model.SeasonDates
import com.example.animewatch.data.model.SeasonEpisodes
import com.example.animewatch.data.model.TmdbShow
import com.example.animewatch.data.remote.anilist.AniListClient
import com.example.animewatch.data.remote.tmdb.TmdbClient
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Puntate (titolo, miniatura, durata, voto) e trama in italiano delle stagioni di un franchise, da TMDB passando dal
 * proxy. TMDB divide le serie diversamente da AniList: le puntate di ogni stagione si trovano con le sue date di
 * uscita ([EpisodeMatch]). Davanti, la cache in memoria e quella condivisa `episodes/{animeId}` di Firestore, come per
 * le schede: la prima persona che apre una serie la scarica per tutti. Senza proxy ([tmdb] null) resta il numero.
 */
class EpisodeRepository(
    private val aniList: AniListClient,
    private val tmdb: TmdbClient?,
    private val db: FirebaseFirestore,
) {
    private val memory = ConcurrentHashMap<Int, SeasonEpisodes>()
    private val collection get() = db.collection("episodes")

    /** false senza proxy: TMDB non si può raggiungere e restano il numero delle puntate e la trama di AniList. */
    val available: Boolean get() = tmdb != null

    /**
     * Pubblica con [publish] le puntate delle stagioni [seasons] (un franchise, in ordine di uscita): prima la cache,
     * anche scaduta, poi TMDB per tutte insieme se qualcuna manca o è scaduta: una sola serie TMDB, una sola chiamata.
     * Se qualcosa fallisce (offline, TMDB giù) non si salva nulla e si riprova alla prossima apertura.
     */
    suspend fun load(seasons: List<Anime>, publish: (Map<Int, SeasonEpisodes>) -> Unit) {
        if (seasons.isEmpty()) return
        val known = readCached(seasons.map { it.id }.toSet())
        if (known.isNotEmpty()) publish(known)
        if (seasons.all { known[it.id]?.isStale(it) == false }) return
        val client = tmdb ?: return

        val fresh = runCatching { fromTmdb(client, seasons, known) }
            .onFailure { logOrRethrow("Puntate TMDB non lette", it) }
            .getOrNull() ?: return
        publish(fresh)
        save(fresh.values)
    }

    private suspend fun fromTmdb(
        client: TmdbClient,
        seasons: List<Anime>,
        known: Map<Int, SeasonEpisodes>,
    ): Map<Int, SeasonEpisodes> {
        val dates = seasons.map { it.id }.chunked(50)
            .flatMap { aniList.seasonDates(it) }
            .associateBy { it.animeId }
        // Si cerca dalla prima stagione TV: TMDB conosce le serie, e un film iniziale (raro) non la troverebbe.
        val anchor = seasons.firstOrNull { it.format in REGULAR } ?: seasons.first()
        val anchorDates = dates[anchor.id] ?: SeasonDates(anchor.id, anchor.format, null, null)

        val cachedId = known.values.firstNotNullOfOrNull { it.tmdbId }
        val show = if (cachedId != null) client.show(cachedId) else findShow(client, anchor, anchorDates)

        return seasons.associate { anime ->
            val season = dates[anime.id] ?: SeasonDates(anime.id, anime.format, null, null)
            anime.id to SeasonEpisodes(
                animeId = anime.id,
                tmdbId = show?.id,
                overview = show?.let { EpisodeMatch.overview(season, it) },
                episodes = show?.let { EpisodeMatch.episodes(season, it) }.orEmpty(),
            )
        }
    }

    /**
     * La serie TMDB della stagione [anchor]: si cerca col titolo romaji e l'anno, poi con quello inglese, poi senza
     * anno, e si accetta solo una serie che abbia davvero le puntate uscite in quelle date. Al massimo 2 candidati
     * per ricerca: ognuno costa una chiamata. null se nessuna va bene.
     */
    private suspend fun findShow(client: TmdbClient, anchor: Anime, dates: SeasonDates): TmdbShow? {
        val year = dates.start?.take(4)?.toIntOrNull() ?: anchor.seasonYear
        val queries = listOfNotNull(
            anchor.title to year,
            anchor.titleEnglish?.takeIf { it.isNotBlank() && it != anchor.title }?.let { it to year },
            anchor.title to null,
        ).distinct()
        val tried = HashSet<Int>()
        for ((query, queryYear) in queries) {
            val candidates = EpisodeMatch.candidates(client.search(query, queryYear), dates).filter { tried.add(it) }
            for (id in candidates) {
                val show = client.show(id)
                if (EpisodeMatch.accepts(show, dates)) return show
            }
        }
        return null
    }

    /** Memoria, poi la copia di Firestore sul dispositivo, poi il server solo per ciò che manca (vedi AnimeRepository). */
    private suspend fun readCached(ids: Set<Int>): Map<Int, SeasonEpisodes> {
        val result = HashMap<Int, SeasonEpisodes>()
        ids.forEach { id -> memory[id]?.let { result[id] = it } }
        for (source in FIRESTORE_SOURCES) {
            val missing = ids.filter { it !in result }
            if (missing.isEmpty()) break
            readFirestore(missing, source).forEach { result[it.animeId] = it }
        }
        memory.putAll(result)
        return result
    }

    private suspend fun readFirestore(ids: List<Int>, source: Source): List<SeasonEpisodes> = coroutineScope {
        ids.chunked(30).map { chunk ->
            async {
                runCatching {
                    collection.whereIn(FieldPath.documentId(), chunk.map { it.toString() }).get(source).await()
                        .documents.mapNotNull { it.toSeasonEpisodes() }
                }.onFailure { logOrRethrow("Lettura cache puntate fallita ($source)", it) }
                    .getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    /** Le scritture non si attendono (vedi UserDataSource). */
    private fun save(entries: Collection<SeasonEpisodes>) {
        if (entries.isEmpty()) return
        entries.forEach { memory[it.animeId] = it }
        val batch = db.batch()
        entries.forEach { batch.set(collection.document(it.animeId.toString()), it.toMap()) }
        batch.commit().addOnFailureListener { AppLog.error(TAG, "Scrittura cache puntate fallita", it) }
    }

    /** Come in AnimeRepository: rete e servizi esterni giù restano fuori da Crashlytics, l'annullamento passa. */
    private fun logOrRethrow(message: String, error: Throwable) {
        if (error is CancellationException) throw error
        AppLog.error(TAG, message, error)
    }

    private companion object {
        const val TAG = "EpisodeRepository"
        val REGULAR = setOf("TV", "TV_SHORT", "ONA")
        val FIRESTORE_SOURCES = listOf(Source.CACHE, Source.DEFAULT)
    }
}
