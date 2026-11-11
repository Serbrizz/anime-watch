package com.example.animewatch.data.remote.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GraphQlRequest(val query: String, val variables: JsonObject)

@Serializable
data class GraphQlResponse<T>(val data: T? = null, val errors: List<GraphQlError>? = null)

@Serializable
data class GraphQlError(val message: String = "")

@Serializable
data class PageData(@SerialName("Page") val page: PageDto)

@Serializable
data class PageDto(val media: List<MediaDto> = emptyList())

@Serializable
data class MediaDto(
    val id: Int,
    val title: TitleDto = TitleDto(),
    val coverImage: CoverDto? = null,
    val genres: List<String> = emptyList(),
    val episodes: Int? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val averageScore: Int? = null,
    val description: String? = null,
    val nextAiringEpisode: NextEpisodeDto? = null,
    val format: String? = null,
    val relations: RelationsDto? = null,
)

@Serializable
data class RelationsDto(val edges: List<RelationEdgeDto> = emptyList())

@Serializable
data class RelationEdgeDto(val relationType: String? = null, val node: RelationNodeDto? = null)

@Serializable
data class RelationNodeDto(val id: Int, val type: String? = null, val format: String? = null)

@Serializable
data class TitleDto(val romaji: String? = null, val english: String? = null)

@Serializable
data class CoverDto(val large: String? = null)

@Serializable
data class NextEpisodeDto(val episode: Int? = null)
