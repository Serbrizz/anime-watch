package com.example.animewatch.data.remote.tmdb

import com.example.animewatch.data.model.TmdbCandidate
import com.example.animewatch.data.model.TmdbEpisode
import com.example.animewatch.data.model.TmdbSeason
import com.example.animewatch.data.model.TmdbShow
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB passando dal proxy (proxy/src/tmdb.js), che aggiunge la chiave e riduce le risposte ai campi usati.
 * Lingua italiana, fissata dal proxy.
 */
interface TmdbApi {
    @GET("tmdb/search")
    suspend fun search(@Query("query") query: String, @Query("year") year: Int?): TmdbSearchDto

    /** La serie con le puntate di tutte le stagioni, speciali (stagione 0) compresi. */
    @GET("tmdb/tv/{id}")
    suspend fun show(@Path("id") id: Int): TmdbShowDto
}

@Serializable
data class TmdbSearchDto(val results: List<TmdbResultDto> = emptyList())

@Serializable
data class TmdbResultDto(val id: Int, val firstAirDate: String? = null, val genreIds: List<Int> = emptyList())

@Serializable
data class TmdbShowDto(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    val firstAirDate: String? = null,
    val seasons: List<TmdbSeasonDto> = emptyList(),
)

@Serializable
data class TmdbSeasonDto(val number: Int, val overview: String? = null, val episodes: List<TmdbEpisodeDto> = emptyList())

@Serializable
data class TmdbEpisodeDto(
    val season: Int,
    val number: Int,
    val airDate: String? = null,
    val name: String? = null,
    val runtime: Int? = null,
    val still: String? = null,
    val vote: Double? = null,
)

class TmdbClient(private val api: TmdbApi) {
    suspend fun search(query: String, year: Int?): List<TmdbCandidate> =
        api.search(query, year).results.map { TmdbCandidate(it.id, it.firstAirDate, it.genreIds) }

    suspend fun show(id: Int): TmdbShow = api.show(id).let { dto ->
        TmdbShow(
            id = dto.id,
            name = dto.name,
            overview = dto.overview,
            firstAirDate = dto.firstAirDate,
            seasons = dto.seasons.map { season ->
                TmdbSeason(
                    number = season.number,
                    overview = season.overview,
                    episodes = season.episodes.map {
                        TmdbEpisode(it.season, it.number, it.airDate, it.name, it.runtime, it.still, it.vote)
                    },
                )
            },
        )
    }
}
