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

    /**
     * I 25 titoli più pertinenti, dal più popolare. Non si chiede `sort: POPULARITY_DESC` ad AniList: fra i
     * titoli che corrispondono metterebbe davanti anche quelli trovati per un sinonimo ("another" dà Re:Zero
     * e altri isekai prima di Another) e i titoli giusti ma poco noti uscirebbero dalla pagina.
     * L'ordinamento è stabile: a parità di popolarità resta quello per pertinenza.
     */
    suspend fun search(text: String): List<Anime> =
        fetch(SEARCH, buildJsonObject { put("search", text) })
            .sortedByDescending { it.popularity ?: 0 }
            .map { it.toAnime() }

    /** AniList accetta al massimo 50 risultati per pagina: chiamare con chunk <= 50. */
    suspend fun byIds(ids: List<Int>): List<Anime> =
        run(BY_IDS, buildJsonObject { putJsonArray("ids") { ids.forEach { add(it) } } })

    /** Anime più popolari di un genere; usati come candidati per i consigliati. */
    suspend fun popularByGenre(genre: String, perPage: Int = 40): List<Anime> =
        run(POPULAR_BY_GENRE, buildJsonObject {
            put("genre", genre)
            put("perPage", perPage)
        })

    private suspend fun run(query: String, variables: JsonObject): List<Anime> =
        fetch(query, variables).map { it.toAnime() }

    private suspend fun fetch(query: String, variables: JsonObject): List<MediaDto> {
        val response = api.execute(GraphQlRequest(query, variables))
        response.errors?.firstOrNull()?.let { throw IOException("AniList: ${it.message}") }
        return response.data?.page?.media.orEmpty()
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
                "media(search: \$search, type: ANIME, isAdult: false, sort: SEARCH_MATCH) { $MEDIA_FIELDS popularity } } }"

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
 * Solo le relazioni che proseguono la storia. Gli speciali restano: quando sono prequel/sequel sono puntate della
 * trama e a volte l'unico anello fra due stagioni (Dr. Stone: Stone Wars -> speciale "Ryusui" -> New World).
 * Le schede annunciate non hanno ancora un formato (null) e restano anche loro. Fuori solo i video musicali.
 */
private fun isSeasonFormat(format: String?): Boolean = format != "MUSIC"

private fun MediaDto.relatedOf(type: String): List<Int> =
    relations?.edges.orEmpty()
        .filter { it.relationType == type && it.node?.type == "ANIME" && isSeasonFormat(it.node.format) }
        .mapNotNull { it.node?.id }
        .distinct()
