package com.example.animewatch.data.remote.openrouter

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class ChatMessage(val role: String, val content: String)

/** Il modello non c'è: lo sceglie il proxy (OPENROUTER_MODEL in proxy/wrangler.toml). */
@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class ChatChoice(val message: ChatMessage)

/**
 * API compatibile OpenAI di OpenRouter, raggiunta attraverso il proxy (BuildConfig.AI_PROXY_URL), che espone lo
 * stesso percorso e aggiunge la chiave.
 */
interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun chat(@Body body: ChatRequest): ChatResponse
}
