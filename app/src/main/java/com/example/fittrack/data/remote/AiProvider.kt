package com.example.fittrack.data.remote

/** One turn of conversation handed to the model. */
data class AiTurn(val fromUser: Boolean, val text: String)

data class AiRequest(
    val systemPrompt: String,
    val turns: List<AiTurn>,
    /** Ask the provider for strict JSON, used for the action/reply response. */
    val expectJson: Boolean
)

sealed class AiResult {
    data class Ok(val text: String) : AiResult()
    object MissingKey : AiResult()
    object Offline : AiResult()
    object RateLimited : AiResult()
    data class Failed(val message: String) : AiResult()
}

/**
 * Everything the assistant needs from a model vendor.
 *
 * Kept deliberately narrow so swapping providers is a binding change in
 * NetworkModule rather than a rewrite: the repository never sees a vendor DTO.
 */
interface AiProvider {
    val isConfigured: Boolean
    suspend fun send(request: AiRequest): AiResult
}
