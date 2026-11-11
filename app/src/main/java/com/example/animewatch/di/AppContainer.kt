package com.example.animewatch.di

import android.content.Context
import com.example.animewatch.BuildConfig
import com.example.animewatch.data.AnimeRepository
import com.example.animewatch.data.firebase.AuthDataSource
import com.example.animewatch.data.firebase.ListDataSource
import com.example.animewatch.data.firebase.UserDataSource
import com.example.animewatch.data.remote.anilist.AniListApi
import com.example.animewatch.data.remote.anilist.AniListClient
import com.example.animewatch.data.remote.openrouter.OpenRouterApi
import com.example.animewatch.data.update.AppUpdater
import com.example.animewatch.data.update.GitHubApi
import com.example.animewatch.domain.LlmReranker
import com.example.animewatch.domain.RecommendationService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
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

    // Download dell'APK: niente callTimeout (dipende dalla connessione), solo quello di lettura.
    private val updateHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // L'API di GitHub rifiuta le richieste senza User-Agent.
            chain.proceed(chain.request().newBuilder().header("User-Agent", "AnimeWatch/${BuildConfig.VERSION_NAME}").build())
        }
        .build()

    private val gitHubApi: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(updateHttp.newBuilder().callTimeout(20, TimeUnit.SECONDS).build())
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()
        .create(GitHubApi::class.java)

    val updater = AppUpdater(
        context = context.applicationContext,
        api = gitHubApi,
        http = updateHttp,
        repo = BuildConfig.UPDATE_REPO,
        currentVersion = BuildConfig.VERSION_NAME,
    )

    private val aniListClient = AniListClient(aniListApi)
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    val auth = AuthDataSource(FirebaseAuth.getInstance(), context.applicationContext)
    val userData = UserDataSource(db)
    val listData = ListDataSource(db)
    val animeRepo = AnimeRepository(aniListClient, db)
    val recommender = RecommendationService(
        aniList = aniListClient,
        animeRepo = animeRepo,
        reranker = LlmReranker(openRouterApi, BuildConfig.OPENROUTER_MODEL),
    )
}
