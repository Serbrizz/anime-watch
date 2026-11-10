package com.example.animewatch.data.firebase

import com.example.animewatch.data.model.Anime
import com.example.animewatch.data.model.AnimeList
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
        cachedAt = getLong("cachedAt") ?: 0L,
    )
}

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
