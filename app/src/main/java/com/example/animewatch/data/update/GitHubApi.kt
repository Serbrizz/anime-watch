package com.example.animewatch.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** API pubblica di GitHub, senza token: funziona solo se il repository delle release è pubblico. */
interface GitHubApi {
    /**
     * Release pubblicate, dalla più recente; senza token le bozze non compaiono, le pre-release sì.
     * Servono tutte quelle più nuove della versione installata, per mostrarne le note insieme.
     */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
    ): Response<List<GitHubRelease>>
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
    /** Impronta calcolata da GitHub ("sha256:<hex>"); assente nelle release più vecchie. */
    val digest: String? = null,
)
