package com.example.animewatch.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** API pubblica di GitHub, senza token: funziona solo se il repository delle release è pubblico. */
interface GitHubApi {
    /** Ultima release pubblicata (escluse bozze e pre-release); 404 se non ce n'è nessuna. */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(@Path("owner") owner: String, @Path("repo") repo: String): Response<GitHubRelease>
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
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
