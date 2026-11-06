package com.example.animewatch.data.remote.openrouter

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class ChatChoice(val message: ChatMessage)

/** API compatibile OpenAI di OpenRouter. Base URL: https://openrouter.ai/api/v1/ */
interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun chat(@Body body: ChatRequest): ChatResponse
}
