package com.example.animewatch.domain

import android.util.Log
import com.example.animewatch.data.model.WatchProgress
import com.example.animewatch.data.remote.ai.AiProxyApi
import com.example.animewatch.data.remote.ai.ChatMessage
import com.example.animewatch.data.remote.ai.ChatRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException

/**
 * Fa ricalcolare al modello l'affinità dei candidati già selezionati dall'algoritmo (che vede solo generi e voto),
 * con un motivo per ciascuno; si tengono i migliori per la sua affinità.
 * Il modello vede SOLO una lista chiusa di candidati reali (ID AniList): non può inventare
 * titoli, può solo valutare e motivare. Qualsiasi errore => null e l'app usa l'algoritmo.
 */
class LlmReranker(private val api: AiProxyApi?) {
    val isAvailable: Boolean get() = api != null

    /** Se il proxy risponderebbe adesso. Qualsiasi errore (rete, 401, 429, proxy vecchio senza /status) => false. */
    suspend fun isReachable(): Boolean {
        val api = api ?: return false
        return try {
            api.status().available
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Preflight AI fallito", e)
            false
        }
    }

    @Serializable
    private data class Pick(val id: Int, val affinity: Int = -1, val reason: String = "")

    suspend fun rerank(
        topGenres: List<String>,
        watchedTitles: List<String>,
        candidates: List<Recommendation>,
        limit: Int,
    ): List<Recommendation>? {
        val api = api ?: return null
        val byId = candidates.associateBy { it.anime.id }
        return try {
            val response = api.chat(
                ChatRequest(
                    messages = listOf(
                        ChatMessage("system", SYSTEM_PROMPT),
                        ChatMessage("user", buildPrompt(topGenres, watchedTitles, candidates)),
                    ),
                    responseFormat = RESPONSE_FORMAT,
                )
            )
            val text = response.choices.firstOrNull()?.message?.content ?: return null
            parse(text, byId, limit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: HttpException) {
            // Il motivo (credito esaurito, modello non disponibile…) è solo nel corpo della risposta.
            val detail = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            Log.w(TAG, "Riordino AI fallito: HTTP ${e.code()} $detail, uso l'algoritmo")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Riordino AI fallito, uso l'algoritmo", e)
            null
        }
    }

    private fun buildPrompt(
        topGenres: List<String>,
        watchedTitles: List<String>,
        candidates: List<Recommendation>,
    ): String = buildString {
        appendLine("Generi preferiti dell'utente (dal più al meno importante): ${topGenres.joinToString()}")
        if (watchedTitles.isNotEmpty()) {
            appendLine("Anime che ha guardato, dai più recenti (fra parentesi quanto ne ha visto): ${watchedTitles.joinToString()}")
        }
        appendLine()
        appendLine("Candidati (id | titolo | generi | voto medio):")
        // Senza l'affinità dell'algoritmo: quando la vedeva, il modello la ricopiava quasi identica (±2 punti).
        candidates.forEach {
            appendLine("${it.anime.id} | ${it.anime.englishOrRomaji} | ${it.anime.genres.joinToString()} | ${it.anime.averageScore ?: "n/d"}")
        }
        appendLine()
        append(
            "Assegna a ogni candidato un'affinità da 0 a 100 con questo utente: non basarti solo sui generi, " +
                "usa ciò che sai di ogni anime (tono, temi, stile, ritmo) e confrontalo con gli anime che ha " +
                "guardato, soprattutto quelli visti per intero. Usa tutta la scala. " +
                "Per ognuno scrivi un motivo in italiano di massimo 20 parole, rivolgendoti all'utente con il tu; " +
                "quando puoi, cita uno degli anime che ha guardato. " +
                "Non citare autori, studi o altri fatti di cui non sei certo. " +
                "Rispondi SOLO con un array JSON con tutti i candidati: " +
                "[{\"id\": 123, \"affinity\": 85, \"reason\": \"...\"}]. Usa solo gli id della lista."
        )
    }

    private fun parse(text: String, byId: Map<Int, Recommendation>, limit: Int): List<Recommendation>? {
        // I modelli a volte avvolgono il JSON in ```...```: si estrae solo l'array.
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val picks = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(Pick.serializer()), text.substring(start, end + 1))
        // Conta l'affinità del modello: si ordina per quella e la si mostra al posto di quella dell'algoritmo.
        val result = picks
            .distinctBy { it.id }
            .filter { it.affinity in 0..100 }
            .mapNotNull { pick -> byId[pick.id]?.copy(score = pick.affinity / 100.0, reason = pick.reason.ifBlank { null }) }
            .sortedByDescending { it.score }
            .take(limit)
        return result.ifEmpty { null }
    }

    companion object {
        private const val TAG = "LlmReranker"
        // In pratica tutti: un limite serve solo a restare sotto i 64 KB che il proxy accetta (200 titoli ≈ 18 KB).
        private const val WATCHED_FOR_PROMPT = 200

        /**
         * Gli anime visti per il prompt, dai più recenti: "Frieren (tutto)", "Bleach (12/366)". Quanto se ne è visto
         * dice al modello cosa è piaciuto davvero, che i soli generi non dicono.
         */
        fun describeWatched(progress: Collection<WatchProgress>, titleOf: (Int) -> String?): List<String> =
            progress
                .filter { it.watchedEpisodes.isNotEmpty() }
                .sortedByDescending { it.updatedAt }
                .mapNotNull { p ->
                    val title = titleOf(p.animeId) ?: return@mapNotNull null
                    val seen = p.watchedEpisodes.size
                    val amount = when {
                        p.totalEpisodes <= 0 -> "$seen puntate"
                        seen >= p.totalEpisodes -> "tutto"
                        else -> "$seen/${p.totalEpisodes}"
                    }
                    "$title ($amount)"
                }
                .take(WATCHED_FOR_PROMPT)

        private const val SYSTEM_PROMPT =
            "Sei un esperto di anime. Rispondi sempre e solo con JSON valido, senza testo extra."

        /**
         * La forma di [Pick], imposta al modello: con la sola richiesta nel prompt un id è arrivato come
         * "21827 | Mob Psycho 100", copiato dalla riga del candidato.
         */
        private val RESPONSE_FORMAT = buildJsonObject {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "picks")
                putJsonObject("schema") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("id") { put("type", "integer") }
                            putJsonObject("affinity") {
                                put("type", "integer")
                                put("minimum", 0)
                                put("maximum", 100)
                            }
                            putJsonObject("reason") { put("type", "string") }
                        }
                        putJsonArray("required") { add("id"); add("affinity"); add("reason") }
                    }
                }
            }
        }
    }
}
