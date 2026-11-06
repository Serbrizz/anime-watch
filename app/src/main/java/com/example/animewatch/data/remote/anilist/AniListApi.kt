package com.example.animewatch.data.remote.anilist

import retrofit2.http.Body
import retrofit2.http.POST

/** AniList espone un unico endpoint GraphQL: POST https://graphql.anilist.co/ */
interface AniListApi {
    @POST("/")
    suspend fun execute(@Body body: GraphQlRequest): GraphQlResponse<PageData>
}
