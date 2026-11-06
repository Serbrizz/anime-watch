package com.example.animewatch.di

import android.content.Context
import com.example.animewatch.BuildConfig
import com.example.animewatch.data.AnimeRepository
import com.example.animewatch.data.UserStore
import com.example.animewatch.data.local.JsonAnimeStore
import com.example.animewatch.data.local.JsonStore
import com.example.animewatch.data.local.JsonUserStore
import com.example.animewatch.data.remote.anilist.AniListApi
import com.example.animewatch.data.remote.anilist.AniListClient
import com.example.animewatch.data.remote.openrouter.OpenRouterApi
import com.example.animewatch.domain.LlmReranker
import com.example.animewatch.domain.RecommendationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Composition root manuale: l'app ha ~6 dipendenze, un framework di DI (Hilt) aggiungerebbe
 * più complessità (plugin KSP, annotazioni) di quanta ne tolga.
 */
class AppContainer(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true // null su campo non-null con default => usa il default
        encodeDefaults = true
    }
    private val jsonMediaType = "application/json".toMediaType()

    private val aniListApi: AniListApi = Retrofit.Builder()
        .baseUrl("https://graphql.anilist.co/")
        .client(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build())
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()
        .create(AniListApi::class.java)

    /** null se la chiave non è configurata: il riordino AI resta semplicemente spento. */
    private val openRouterApi: OpenRouterApi? =
        BuildConfig.OPENROUTER_API_KEY.takeIf { it.isNotBlank() }?.let { key ->
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $key")
                            .header("X-Title", "AnimeWatch")
                            .build()
                    )
                }
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
            Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .client(client)
                .addConverterFactory(json.asConverterFactory(jsonMediaType))
                .build()
                .create(OpenRouterApi::class.java)
        }

    private val aniListClient = AniListClient(aniListApi)

    // ---- PERSISTENZA TEMPORANEA ----------------------------------------------------------
    // Quando il database vero è pronto: sostituire queste righe con le sue implementazioni di
    // UserStore e AnimeStore ed eliminare data/local/ (vedi data/Stores.kt).
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val files = JsonStore(File(context.filesDir, "database"))
    val userStore: UserStore = JsonUserStore(files, persistenceScope)
    val animeRepo = AnimeRepository(aniListClient, JsonAnimeStore(files, persistenceScope))
    // --------------------------------------------------------------------------------------

    val recommender = RecommendationService(
        aniList = aniListClient,
        animeRepo = animeRepo,
        reranker = LlmReranker(openRouterApi, BuildConfig.OPENROUTER_MODEL),
    )
}
