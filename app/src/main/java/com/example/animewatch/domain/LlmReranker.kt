package com.example.animewatch.domain

import android.util.Log
import com.example.animewatch.data.remote.openrouter.ChatMessage
import com.example.animewatch.data.remote.openrouter.ChatRequest
import com.example.animewatch.data.remote.openrouter.OpenRouterApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Riordina i candidati già selezionati dall'algoritmo e ne spiega il motivo.
 * Il modello vede SOLO una lista chiusa di candidati reali (ID AniList): non può inventare
 * titoli, può solo scegliere e motivare. Qualsiasi errore => null e l'app usa l'algoritmo.
 */
class LlmReranker(
    private val api: OpenRouterApi?,
    private val model: String,
) {
    val isAvailable: Boolean get() = api != null

    @Serializable
    private data class Pick(val id: Int, val reason: String = "")

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
                    model = model,
                    messages = listOf(
                        ChatMessage("system", SYSTEM_PROMPT),
                        ChatMessage("user", buildPrompt(topGenres, watchedTitles, candidates, limit)),
                    ),
                )
            )
            val text = response.choices.firstOrNull()?.message?.content ?: return null
            parse(text, byId, limit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Riordino OpenRouter fallito, uso l'algoritmo", e)
            null
        }
    }

    private fun buildPrompt(
        topGenres: List<String>,
        watchedTitles: List<String>,
        candidates: List<Recommendation>,
        limit: Int,
    ): String = buildString {
        appendLine("Generi preferiti dell'utente (dal più al meno importante): ${topGenres.joinToString()}")
        if (watchedTitles.isNotEmpty()) {
            appendLine("Anime che ha già guardato: ${watchedTitles.joinToString()}")
        }
        appendLine()
        appendLine("Candidati (id | titolo | generi | voto medio):")
        candidates.forEach {
            appendLine("${it.anime.id} | ${it.anime.title} | ${it.anime.genres.joinToString()} | ${it.anime.averageScore ?: "n/d"}")
        }
        appendLine()
        append(
            "Scegli i $limit candidati migliori per questo utente, dal migliore al peggiore. " +
                "Privilegia la varietà ed evita titoli quasi identici. " +
                "Per ognuno scrivi un motivo in italiano di massimo 20 parole. " +
                "Rispondi SOLO con un array JSON: [{\"id\": 123, \"reason\": \"...\"}]. " +
                "Usa solo gli id della lista."
        )
    }

    private fun parse(text: String, byId: Map<Int, Recommendation>, limit: Int): List<Recommendation>? {
        // I modelli a volte avvolgono il JSON in ```...```: si estrae solo l'array.
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val picks = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(Pick.serializer()), text.substring(start, end + 1))
        val result = picks
            .distinctBy { it.id }
            .mapNotNull { pick -> byId[pick.id]?.copy(reason = pick.reason.ifBlank { null }) }
            .take(limit)
        return result.ifEmpty { null }
    }

    private companion object {
        const val TAG = "LlmReranker"
        const val SYSTEM_PROMPT =
            "Sei un esperto di anime. Rispondi sempre e solo con JSON valido, senza testo extra."
    }
}
