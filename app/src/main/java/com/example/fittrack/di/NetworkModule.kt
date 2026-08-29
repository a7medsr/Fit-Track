package com.example.fittrack.di

import com.example.fittrack.BuildConfig
import com.example.fittrack.data.remote.AiProvider
import com.example.fittrack.data.remote.AvatarApi
import com.example.fittrack.data.remote.OpenAiCompatibleApi
import com.example.fittrack.data.remote.PostImageApi
import com.example.fittrack.data.remote.OpenAiCompatibleProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Generous: a model can take a while to produce its first token.
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideOpenAiCompatibleApi(client: OkHttpClient): OpenAiCompatibleApi =
        Retrofit.Builder()
            .baseUrl(baseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiCompatibleApi::class.java)

    /** Talks to the FitTrack service on the VPS, not to a model vendor. */
    @Provides
    @Singleton
    fun provideAvatarApi(client: OkHttpClient): AvatarApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.VPS_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AvatarApi::class.java)

    /** Community post photos, on the same VPS as the avatars. */
    @Provides
    @Singleton
    fun providePostImageApi(client: OkHttpClient): PostImageApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.VPS_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PostImageApi::class.java)

    /**
     * Gemini by default: it has a genuine free tier and no card requirement.
     * Set `AI_PROVIDER=xai` in local.properties to point at Grok instead --
     * both speak the same OpenAI-compatible shape, so nothing else changes.
     */
    @Provides
    @Singleton
    fun provideAiProvider(api: OpenAiCompatibleApi): AiProvider =
        OpenAiCompatibleProvider(api, apiKey(), model())

    private fun usingXai(): Boolean =
        BuildConfig.AI_PROVIDER.equals("xai", ignoreCase = true)

    private fun baseUrl(): String = if (usingXai()) XAI_BASE_URL else GEMINI_BASE_URL

    private fun model(): String {
        if (usingXai()) return XAI_MODEL
        val configured: String = BuildConfig.GEMINI_MODEL
        return if (configured.isBlank()) GEMINI_MODEL else configured
    }

    private fun apiKey(): String =
        if (usingXai()) BuildConfig.XAI_API_KEY else BuildConfig.GEMINI_API_KEY

    /** Google's OpenAI-compatible surface, not the native generateContent route. */
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/"
    private const val GEMINI_MODEL = "gemini-flash-lite-latest"
    private const val XAI_BASE_URL = "https://api.x.ai/v1/"
    private const val XAI_MODEL = "grok-4.6"
}
