package com.example.animewatch.data.remote.anilist

import com.example.animewatch.data.model.Anime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException

/** Livello sottile sopra AniListApi: costruisce le query e converte i DTO in [Anime]. */
class AniListClient(private val api: AniListApi) {

    suspend fun search(text: String): List<Anime> =
        run(SEARCH, buildJsonObject { put("search", text) })

    /** AniList accetta al massimo 50 risultati per pagina: chiamare con chunk <= 50. */
    suspend fun byIds(ids: List<Int>): List<Anime> =
        run(BY_IDS, buildJsonObject { putJsonArray("ids") { ids.forEach { add(it) } } })

    /** Anime più popolari di un genere; usati come candidati per i consigliati. */
    suspend fun popularByGenre(genre: String, perPage: Int = 40): List<Anime> =
        run(POPULAR_BY_GENRE, buildJsonObject {
            put("genre", genre)
            put("perPage", perPage)
        })

    private suspend fun run(query: String, variables: JsonObject): List<Anime> {
        val response = api.execute(GraphQlRequest(query, variables))
        response.errors?.firstOrNull()?.let { throw IOException("AniList: ${it.message}") }
        return response.data?.page?.media.orEmpty().map { it.toAnime() }
    }

    private companion object {
        // \$ = simbolo letterale delle variabili GraphQL; $MEDIA_FIELDS = interpolazione Kotlin.
        const val MEDIA_FIELDS =
            "id title { romaji english } coverImage { large } genres episodes status " +
                "season seasonYear averageScore description(asHtml: false) " +
                "nextAiringEpisode { episode } format " +
                "relations { edges { relationType(version: 2) node { id type format } } }"

        val SEARCH =
            "query (\$search: String) { Page(page: 1, perPage: 25) { " +
                "media(search: \$search, type: ANIME, isAdult: false, sort: SEARCH_MATCH) { $MEDIA_FIELDS } } }"

        val BY_IDS =
            "query (\$ids: [Int]) { Page(page: 1, perPage: 50) { " +
                "media(id_in: \$ids, type: ANIME) { $MEDIA_FIELDS } } }"

        val POPULAR_BY_GENRE =
            "query (\$genre: String, \$perPage: Int) { Page(page: 1, perPage: \$perPage) { " +
                "media(genre: \$genre, type: ANIME, isAdult: false, format_in: [TV, MOVIE, OVA, ONA], " +
                "sort: POPULARITY_DESC) { $MEDIA_FIELDS } } }"
    }
}

private val BR_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val TAG_REGEX = Regex("<[^>]+>")

private fun String.cleanHtml(): String =
    replace(BR_REGEX, "\n").replace(TAG_REGEX, "").trim()

internal fun MediaDto.toAnime(): Anime = Anime(
    id = id,
    title = title.romaji ?: title.english ?: "Senza titolo",
    titleEnglish = title.english,
    coverUrl = coverImage?.large,
    genres = genres,
    episodes = episodes,
    airedEpisodes = nextAiringEpisode?.episode?.minus(1)?.coerceAtLeast(0),
    status = status,
    season = season,
    seasonYear = seasonYear,
    averageScore = averageScore,
    description = description?.cleanHtml()?.take(2000),
    format = format,
    prequelIds = relatedOf("PREQUEL"),
    sequelIds = relatedOf("SEQUEL"),
)

/**
 * Solo le relazioni che proseguono la storia, verso schede che possono essere una "stagione".
 * Speciali e video musicali restano fuori: spesso sono riassunti o extra, non puntate da seguire.
 */
private val SEASON_FORMATS = setOf("TV", "TV_SHORT", "ONA", "OVA", "MOVIE")

private fun MediaDto.relatedOf(type: String): List<Int> =
    relations?.edges.orEmpty()
        .filter { it.relationType == type && it.node?.type == "ANIME" && it.node.format in SEASON_FORMATS }
        .mapNotNull { it.node?.id }
        .distinct()
