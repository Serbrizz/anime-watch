package com.example.animewatch.data.remote.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class ChatMessage(val role: String, val content: String)

/** Il modello non c'è: lo sceglie il proxy (GEMINI_MODEL in proxy/wrangler.toml). */
@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    /** Formato OpenAI `{type: "json_schema", json_schema: {name, schema}}`: il proxy obbliga il modello a rispettarlo. */
    @SerialName("response_format") val responseFormat: JsonObject? = null,
)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class ChatChoice(val message: ChatMessage)

/**
 * Il proxy AI (BuildConfig.AI_PROXY_URL, Cloudflare Worker in proxy/): parla il formato chat/completions di OpenAI,
 * lo traduce per Gemini e aggiunge la chiave, che resta sul Worker.
 */
interface AiProxyApi {
    @POST("chat/completions")
    suspend fun chat(@Body body: ChatRequest): ChatResponse

    /** Preflight del proxy: se l'AI risponderebbe adesso (chiave valida, modello esistente), senza consumare quota. */
    @GET("status")
    suspend fun status(): ProxyStatus
}

@Serializable
data class ProxyStatus(val available: Boolean = false)
