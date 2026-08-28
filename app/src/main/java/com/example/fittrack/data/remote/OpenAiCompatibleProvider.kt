package com.example.fittrack.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One provider for every vendor that speaks the OpenAI `chat/completions`
 * shape.
 *
 * Gemini exposes an OpenAI-compatible endpoint at
 * `https://generativelanguage.googleapis.com/v1beta/openai/`, and xAI's API is
 * the same shape, so both are reached through this single implementation.
 * Switching vendors is a base URL, a model name and a key.
 */
interface OpenAiCompatibleApi {

    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body body: ChatCompletionRequest
    ): ChatCompletionResponse
}

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatCompletionMessage>,
    val temperature: Double,
    @SerializedName("max_tokens") val maxTokens: Int,
    @SerializedName("response_format") val responseFormat: ResponseFormat?
)

data class ChatCompletionMessage(val role: String, val content: String)

data class ResponseFormat(val type: String)

data class ChatCompletionResponse(val choices: List<ChatCompletionChoice>?) {
    fun firstText(): String? =
        choices?.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
}

data class ChatCompletionChoice(val message: ChatCompletionMessage?)

@Singleton
class OpenAiCompatibleProvider @Inject constructor(
    private val api: OpenAiCompatibleApi,
    private val apiKey: String,
    private val model: String
) : AiProvider {

    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override suspend fun send(request: AiRequest): AiResult {
        if (!isConfigured) return AiResult.MissingKey

        val messages = buildList {
            add(ChatCompletionMessage("system", request.systemPrompt))
            request.turns.forEach { turn ->
                add(ChatCompletionMessage(if (turn.fromUser) "user" else "assistant", turn.text))
            }
        }

        val body = ChatCompletionRequest(
            model = model,
            messages = messages,
            // Low: this is parsing intent into a fixed schema, not writing prose.
            temperature = 0.2,
            maxTokens = MAX_OUTPUT_TOKENS,
            responseFormat = if (request.expectJson) ResponseFormat("json_object") else null
        )

        return try {
            api.chat("Bearer $apiKey", body).firstText()
                ?.let { AiResult.Ok(it) }
                ?: AiResult.Failed("The assistant returned an empty response.")
        } catch (e: HttpException) {
            when (e.code()) {
                429 -> AiResult.RateLimited
                // 400 covers a malformed key, which is the most likely
                // misconfiguration: an xAI key sent to Google, or vice versa.
                400, 401, 403 -> AiResult.MissingKey
                else -> AiResult.Failed("Assistant error ${e.code()}.")
            }
        } catch (e: IOException) {
            AiResult.Offline
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Assistant request failed.")
        }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 600
    }
}
