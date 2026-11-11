package com.example.animewatch.di

import android.content.Context
import com.example.animewatch.BuildConfig
import com.example.animewatch.data.AnimeRepository
import com.example.animewatch.data.EpisodeRepository
import com.example.animewatch.data.remote.tmdb.TmdbApi
import com.example.animewatch.data.remote.tmdb.TmdbClient
import com.example.animewatch.data.firebase.AuthDataSource
import com.example.animewatch.data.firebase.ListDataSource
import com.example.animewatch.data.firebase.UserDataSource
import com.example.animewatch.data.remote.anilist.AniListApi
import com.example.animewatch.data.remote.anilist.AniListClient
import com.example.animewatch.data.remote.ai.AiProxyApi
import com.example.animewatch.data.update.AppUpdater
import com.example.animewatch.data.update.GitHubApi
import com.example.animewatch.domain.LlmReranker
import com.example.animewatch.domain.RecommendationService
import com.google.android.gms.tasks.Tasks
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

    private val firebaseAuth = FirebaseAuth.getInstance()

    /**
     * Il proxy (proxy/, Cloudflare Worker) custodisce le chiavi di Gemini e TMDB: l'app si presenta con l'ID token
     * Firebase dell'utente. null se il proxy non è configurato: il riordino AI resta spento e le puntate senza titoli.
     */
    private val proxyHttp: OkHttpClient? = BuildConfig.AI_PROXY_URL.takeIf { it.isNotBlank() }?.let {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Gli interceptor girano su un thread di OkHttp: si può attendere il token (rinnovato se scaduto).
                // Senza utente si parte senza token: il proxy risponde 401.
                val token = firebaseAuth.currentUser?.let { user ->
                    runCatching { Tasks.await(user.getIdToken(false), 15, TimeUnit.SECONDS).token }.getOrNull()
                }
                val request = chain.request().newBuilder()
                if (token != null) request.header("Authorization", "Bearer $token")
                chain.proceed(request.build())
            }
            .build()
    }

    private fun <T> proxyApi(client: OkHttpClient, api: Class<T>): T = Retrofit.Builder()
        .baseUrl(BuildConfig.AI_PROXY_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()
        .create(api)

    // Gemini di solito risponde in pochi secondi, ma un modello lento o carico può metterci anche un minuto prima
    // che arrivi il primo byte. Anche la lettura va allungata: quella predefinita (10 s) chiudeva prima.
    private val aiProxyApi: AiProxyApi? = proxyHttp?.let { http ->
        val slow = http.newBuilder().readTimeout(90, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
        proxyApi(slow, AiProxyApi::class.java)
    }

    // Una serie lunga sono due o tre chiamate a TMDB dentro il Worker.
    private val tmdbApi: TmdbApi? = proxyHttp?.let { http ->
        proxyApi(http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build(), TmdbApi::class.java)
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

    val auth = AuthDataSource(firebaseAuth, context.applicationContext)
    val userData = UserDataSource(db)
    val listData = ListDataSource(db)
    val animeRepo = AnimeRepository(aniListClient, db)
    val episodeRepo = EpisodeRepository(aniListClient, tmdbApi?.let(::TmdbClient), db)
    val recommender = RecommendationService(
        aniList = aniListClient,
        animeRepo = animeRepo,
        reranker = LlmReranker(aiProxyApi),
    )
}
