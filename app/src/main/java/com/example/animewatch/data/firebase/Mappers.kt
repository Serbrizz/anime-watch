package com.example.animewatch.data.firebase

import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.Episode
import com.example.animewatch.data.model.SeasonEpisodes
import com.example.animewatch.data.model.ListInvite
import com.example.animewatch.data.model.ListRole
import com.example.animewatch.data.model.MemberProfile
import com.example.animewatch.data.model.UserProfile
import com.example.animewatch.data.model.WatchProgress
import com.google.firebase.firestore.DocumentSnapshot

/** Firestore restituisce tutti gli interi come Long: qui si riconverte in modo sicuro. */
private fun DocumentSnapshot.int(field: String): Int? = getLong(field)?.toInt()

private fun DocumentSnapshot.intList(field: String): List<Int> =
    (get(field) as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }

private fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>).orEmpty().filterIsInstance<String>()

/** I campi null non vengono scritti: le regole Firestore controllano solo i campi presenti. */
fun Anime.toMap(): Map<String, Any> = buildMap {
    put("id", id)
    put("title", title)
    put("genres", genres)
    put("cachedAt", cachedAt)
    put("v", ANIME_CACHE_VERSION)
    put("prequelIds", prequelIds)
    put("sequelIds", sequelIds)
    format?.let { put("format", it) }
    titleEnglish?.let { put("titleEnglish", it) }
    coverUrl?.let { put("coverUrl", it) }
    episodes?.let { put("episodes", it) }
    airedEpisodes?.let { put("airedEpisodes", it) }
    status?.let { put("status", it) }
    season?.let { put("season", it) }
    seasonYear?.let { put("seasonYear", it) }
    averageScore?.let { put("averageScore", it) }
    description?.let { put("description", it) }
}

fun DocumentSnapshot.toAnime(): Anime? {
    if (!exists()) return null
    val id = int("id") ?: return null
    val title = getString("title") ?: return null
    return Anime(
        id = id,
        title = title,
        titleEnglish = getString("titleEnglish"),
        coverUrl = getString("coverUrl"),
        genres = stringList("genres"),
        episodes = int("episodes"),
        airedEpisodes = int("airedEpisodes"),
        status = getString("status"),
        season = getString("season"),
        seasonYear = int("seasonYear"),
        averageScore = int("averageScore"),
        description = getString("description"),
        format = getString("format"),
        prequelIds = intList("prequelIds"),
        sequelIds = intList("sequelIds"),
        // Scritte da una versione dell'app che leggeva AniList in modo diverso: scadute subito, si rileggono.
        cachedAt = if ((getLong("v") ?: 0L) >= ANIME_CACHE_VERSION) getLong("cachedAt") ?: 0L else 0L,
    )
}

/**
 * Versione del formato delle schede in `anime/`: si incrementa quando cambia ciò che si ricava da AniList
 * (campi nuovi, filtri sulle relazioni), così le schede già in cache vengono rilette invece di restare
 * sbagliate fino alla scadenza. 1 = relazioni che includono speciali e schede senza formato.
 */
private const val ANIME_CACHE_VERSION = 1L

/**
 * Puntate di una stagione in `episodes/{id}`. Per ogni puntata `n` e, solo se ci sono, `title`, `thumb`, `len`
 * (minuti) e `score` (0-10): chiavi brevi perché una stagione lunga ne ha centinaia in un documento.
 */
fun SeasonEpisodes.toMap(): Map<String, Any> = buildMap {
    put("id", animeId)
    put("cachedAt", cachedAt)
    put("v", EPISODES_CACHE_VERSION)
    tmdbId?.let { put("tmdbId", it) }
    overview?.let { put("overview", it.take(MAX_OVERVIEW)) }
    put("episodes", episodes.take(MAX_EPISODES).map { episode ->
        buildMap {
            put("n", episode.number)
            episode.title?.let { put("title", it) }
            episode.thumbnailUrl?.let { put("thumb", it) }
            episode.length?.let { put("len", it) }
            episode.score?.let { put("score", it) }
        }
    })
}

fun DocumentSnapshot.toSeasonEpisodes(): SeasonEpisodes? {
    if (!exists()) return null
    val id = int("id") ?: return null
    val episodes = (get("episodes") as? List<*>).orEmpty().mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        Episode(
            number = (map["n"] as? Number)?.toInt() ?: return@mapNotNull null,
            title = map["title"] as? String,
            thumbnailUrl = map["thumb"] as? String,
            length = (map["len"] as? Number)?.toInt(),
            score = (map["score"] as? Number)?.toDouble(),
        )
    }
    return SeasonEpisodes(
        animeId = id,
        tmdbId = int("tmdbId"),
        overview = getString("overview"),
        episodes = episodes,
        // Scritte da una versione con un'altra fonte (Kitsu): scadute subito, si rileggono da TMDB.
        cachedAt = if ((getLong("v") ?: 0L) >= EPISODES_CACHE_VERSION) getLong("cachedAt") ?: 0L else 0L,
    )
}

/** Come [ANIME_CACHE_VERSION], per `episodes/`. 2 = TMDB, 3 = trama della stagione TMDB anche per le "Part 2". */
private const val EPISODES_CACHE_VERSION = 3L

/** Stessi limiti delle regole Firestore su `episodes/`. */
private const val MAX_EPISODES = 2000
private const val MAX_OVERVIEW = 4000

fun DocumentSnapshot.toAnimeList(): AnimeList? {
    if (!exists()) return null
    val members = (get("members") as? Map<*, *>).orEmpty()
        .mapNotNull { (uid, role) -> ListRole.fromKey(role as? String)?.let { (uid as String) to it } }
        .toMap()
    val profiles = (get("profiles") as? Map<*, *>).orEmpty()
        .mapNotNull { (uid, value) ->
            val p = value as? Map<*, *> ?: return@mapNotNull null
            val name = p["name"] as? String ?: return@mapNotNull null
            (uid as String) to MemberProfile(name, p["photoUrl"] as? String)
        }
        .toMap()
    return AnimeList(
        id = id,
        name = getString("name") ?: return null,
        createdAt = getLong("createdAt") ?: 0L,
        animeIds = intList("animeIds"),
        ownerId = getString("ownerId") ?: return null,
        members = members,
        profiles = profiles,
    )
}

/** Il campo `photoUrl` si omette se manca, come per gli altri campi null. */
fun UserProfile.toMemberMap(): Map<String, Any> = buildMap {
    put("name", name.take(MAX_NAME))
    photoUrl?.let { put("photoUrl", it) }
}

fun ListInvite.toMap(): Map<String, Any> = mapOf(
    "listId" to listId,
    "listName" to listName,
    "role" to role.key,
    "fromUid" to fromUid,
    "fromName" to fromName.take(MAX_NAME),
    "toEmail" to toEmail,
    "createdAt" to createdAt,
)

fun DocumentSnapshot.toListInvite(): ListInvite? {
    if (!exists()) return null
    return ListInvite(
        listId = getString("listId") ?: return null,
        listName = getString("listName") ?: "",
        role = ListRole.fromKey(getString("role")) ?: return null,
        fromUid = getString("fromUid") ?: return null,
        fromName = getString("fromName") ?: "",
        toEmail = getString("toEmail") ?: return null,
        createdAt = getLong("createdAt") ?: 0L,
    )
}

/** Stesso limite delle regole Firestore sui nomi. */
const val MAX_NAME = 100

fun WatchProgress.toMap(): Map<String, Any> = mapOf(
    "animeId" to animeId,
    "watchedEpisodes" to watchedEpisodes.sorted(),
    "genres" to genres,
    "totalEpisodes" to totalEpisodes,
    "updatedAt" to updatedAt,
)

fun DocumentSnapshot.toWatchProgress(): WatchProgress? {
    if (!exists()) return null
    return WatchProgress(
        animeId = int("animeId") ?: id.toIntOrNull() ?: return null,
        watchedEpisodes = intList("watchedEpisodes").toSet(),
        genres = stringList("genres"),
        totalEpisodes = int("totalEpisodes") ?: 0,
        updatedAt = getLong("updatedAt") ?: 0L,
    )
}
