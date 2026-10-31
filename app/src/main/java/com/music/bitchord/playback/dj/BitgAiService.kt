package com.music.bitchord.playback.dj

import android.content.Context
import android.util.Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.favorites.LocalFavoritesStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Real AI Intelligence Engine for BitG.
 *
 * Connects to state-of-the-art LLM providers (Google Gemini, OpenAI, Groq, Anthropic Claude,
 * or custom OpenAI-compatible endpoints) and leverages continuous listening history, top artists,
 * genres, and user favorites to formulate context-aware DJ sets and personalized spoken commentary.
 */
object BitgAiService {

    private const val TAG = "BitgAiService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        Http.client.newBuilder()
            .callTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class BitgAiResult(
        val vibeTitle: String,
        val spokenCommentary: String,
        val searchQueries: List<String>,
    )

    fun isAiConfigured(): Boolean {
        return AppSettings.bitgApiKey.value.isNotBlank()
    }

    fun getProviderLabel(providerCode: String = AppSettings.bitgAiProvider.value): String {
        return when (providerCode.lowercase()) {
            "gemini" -> "Google Gemini"
            "openai" -> "OpenAI"
            "groq" -> "Groq"
            "anthropic" -> "Anthropic Claude"
            "custom" -> "Custom / OpenRouter"
            else -> providerCode
        }
    }

    /**
     * Extracts and synthesizes a rich musical taste profile from device listening stats and favorites.
     */
    suspend fun buildUserTasteProfile(context: Context): String = withContext(Dispatchers.IO) {
        val summary = runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }.getOrNull()
        val favorites = runCatching { LocalFavoritesStore.getFavorites() }.getOrDefault(emptyList())

        val topArtists = summary?.artists?.take(10)?.joinToString(", ") { entry ->
            "${entry.title} (${entry.plays} écoutes)"
        }
        val topTracks = summary?.songs?.take(10)?.joinToString(", ") { ranked ->
            "${ranked.song.title} - ${ranked.song.artist} (${ranked.plays} écoutes)"
        }
        val topGenres = summary?.genres?.take(6)?.joinToString(", ") { genre ->
            genre.title
        }
        val favTitles = favorites.take(10).joinToString(", ") { song ->
            "${song.title} (${song.artist})"
        }

        buildString {
            if (!topArtists.isNullOrBlank()) {
                appendLine("• Artistes les plus écoutés : $topArtists")
            }
            if (!topGenres.isNullOrBlank()) {
                appendLine("• Genres dominants : $topGenres")
            }
            if (!topTracks.isNullOrBlank()) {
                appendLine("• Morceaux les plus écoutés : $topTracks")
            }
            if (favTitles.isNotBlank()) {
                appendLine("• Titres favoris / likés : $favTitles")
            }
            if (isEmpty()) {
                appendLine("• Profil : Auditeur en phase de découverte, ouvert à tous les genres.")
            }
        }
    }

    /**
     * Tests connectivity to the configured AI provider.
     */
    suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = AppSettings.bitgApiKey.value.trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Aucune clé API configurée"))
        }

        val provider = AppSettings.bitgAiProvider.value.lowercase()
        val isFrench = AppSettings.bitgLanguage.value == "fr" ||
            (AppSettings.bitgLanguage.value == "auto" && java.util.Locale.getDefault().language == "fr")

        val testPrompt = if (isFrench) {
            "Dis bonjour et confirme en une courte phrase que la connexion au DJ BitG fonctionne parfaitement."
        } else {
            "Say hello and confirm in one short sentence that the BitG DJ AI connection is working."
        }

        runCatching {
            when (provider) {
                "gemini" -> callGeminiRaw(testPrompt, apiKey, AppSettings.bitgCustomModel.value)
                "anthropic", "claude" -> callAnthropicRaw(testPrompt, apiKey, AppSettings.bitgCustomModel.value)
                else -> {
                    val endpoint = when (provider) {
                        "groq" -> "https://api.groq.com/openai/v1/chat/completions"
                        "custom" -> AppSettings.bitgCustomEndpoint.value.ifBlank { "https://openrouter.ai/api/v1/chat/completions" }
                        else -> "https://api.openai.com/v1/chat/completions"
                    }
                    val defaultModel = when (provider) {
                        "groq" -> "llama-3.3-70b-versatile"
                        else -> "gpt-4o-mini"
                    }
                    val model = AppSettings.bitgCustomModel.value.ifBlank { defaultModel }
                    callOpenAiCompatibleRaw(endpoint, apiKey, model, testPrompt)
                }
            }
        }
    }

    /**
     * Queries the AI model with listener context to produce personalized commentary and playlist queries.
     */
    suspend fun generateAiDjSession(
        context: Context,
        userPrompt: String,
        vibe: AiDjManager.DjVibe? = null,
        currentSong: Song? = null,
        isFrench: Boolean = true,
    ): Result<BitgAiResult> = withContext(Dispatchers.IO) {
        val apiKey = AppSettings.bitgApiKey.value.trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Aucune clé API configurée pour BitG"))
        }

        val tasteProfile = buildUserTasteProfile(context)
        val currentContext = buildString {
            if (currentSong != null) {
                appendLine("En cours d'écoute ou dernier morceau : ${currentSong.title} par ${currentSong.artist}")
            }
            if (vibe != null) {
                appendLine("Vibe demandée : ${if (isFrench) vibe.labelFr else vibe.labelEn}")
            }
        }

        val systemPrompt = buildSystemPrompt(isFrench)
        val userContent = buildString {
            appendLine("HISTORIQUE ET GOÛTS DU REPLAY DE L'UTILISATEUR :")
            appendLine(tasteProfile)
            if (currentContext.isNotBlank()) {
                appendLine("CONTEXTE ACTUEL :")
                appendLine(currentContext)
            }
            appendLine("DEMANDE / REQUÊTE :")
            appendLine(userPrompt.ifBlank { "Lancer une session personnalisée basée sur ce que j'aime." })
        }

        val provider = AppSettings.bitgAiProvider.value.lowercase()
        val rawResponseResult = runCatching {
            when (provider) {
                "gemini" -> callGeminiJson(systemPrompt, userContent, apiKey, AppSettings.bitgCustomModel.value)
                "anthropic", "claude" -> callAnthropicJson(systemPrompt, userContent, apiKey, AppSettings.bitgCustomModel.value)
                else -> {
                    val endpoint = when (provider) {
                        "groq" -> "https://api.groq.com/openai/v1/chat/completions"
                        "custom" -> AppSettings.bitgCustomEndpoint.value.ifBlank { "https://openrouter.ai/api/v1/chat/completions" }
                        else -> "https://api.openai.com/v1/chat/completions"
                    }
                    val defaultModel = when (provider) {
                        "groq" -> "llama-3.3-70b-versatile"
                        else -> "gpt-4o-mini"
                    }
                    val model = AppSettings.bitgCustomModel.value.ifBlank { defaultModel }
                    callOpenAiCompatibleJson(endpoint, apiKey, model, systemPrompt, userContent)
                }
            }
        }

        rawResponseResult.mapCatching { jsonText ->
            parseAiJsonResponse(jsonText, isFrench)
        }.onFailure { err ->
            Log.e(TAG, "Error generating AI DJ session: ${err.message}", err)
        }
    }

    private fun buildSystemPrompt(isFrench: Boolean): String {
        return if (isFrench) {
            """
            Tu es BitG, le DJ IA charismatique, ultra-stylé et fin connaisseur musical de l'application BitChard.
            Tu animes la session de l'auditeur comme le DJ officiel Spotify ou l'animateur d'une radio nocturne légendaire.

            DIRECTIVES CAPITALES :
            1. INTERDICTION DE PERROQUETER :
               - Ne répète JAMAIS ce que l'utilisateur a écrit ou dit.
               - Interdiction formelle de phrases types : "Tu as demandé X", "Voici du X", "Comme tu as voulu X", "Tu m'as demandé X", etc.
               - Entre directement dans l'ambiance, parle du rythme, des instruments, de la basse, de la vibe ou de l'énergie.
            2. Commentaire parlé (spokenCommentary) :
               - Percutant, chaleureux, spontané et très naturel (tutoiement "tu", "ton").
               - Fais des liens musicaux subtils et intelligents avec son Replay et ses artistes favoris, sans jamais être mécanique.
               - TRÈS CONCIS : 1 à 2 phrases courtes et fluides maximum (environ 15 à 25 mots au total, ce texte est lu à voix haute).
            3. Titre de session (vibeTitle) :
               - Un titre de 2 à 4 mots percutant et élégant (ex: "Nocturne Feutré", "Vague Synthwave", "Kick & Basslines", "Deep Focus").
            4. Requêtes YouTube Music (searchQueries) :
               - 4 à 6 requêtes d'artistes ou de morceaux spécifiques et pertinents pour composer le mix.
            5. Zéro IA générative :
               - Aucun contenu Suno, Udio, ni reprise IA artificielle. Exclusivement de véritables artistes musicaux.

            Réponds EXCLUSIVEMENT avec un objet JSON valide (aucun texte avant ou après, pas de bloc Markdown) sous cette forme :
            {
              "vibeTitle": "Titre Court",
              "spokenCommentary": "Commentaire parlé fluide du DJ",
              "searchQueries": ["Requête 1", "Requête 2", "Requête 3", "Requête 4"]
            }
            """.trimIndent()
        } else {
            """
            You are BitG, the charismatic, ultra-slick and music-savvy AI DJ for the BitChard app.
            You curate and host the listener's session with effortless style, deep musical taste, and radio-DJ flair.

            MANDATORY RULES:
            1. NEVER PARROT THE USER'S PROMPT:
               - Do NOT quote or repeat the user's words.
               - Forbidden phrases: "You asked for X", "Here is some X", "Since you wanted X", etc.
               - Talk directly about the music, the groove, the tempo, or the atmosphere.
            2. Spoken commentary (spokenCommentary):
               - Natural, witty, engaging and smooth DJ delivery.
               - VERY CONCISE: 1 to 2 punchy sentences maximum (around 15 to 25 words total, spoken aloud).
            3. Session title (vibeTitle):
               - 2 to 4 clean words (e.g. "Late Night Drift", "Peak Energy Flow", "Lo-Fi Haven").
            4. Search queries (searchQueries):
               - 4 to 6 accurate artist or track queries for YouTube Music.
            5. No synthetic content:
               - No Suno, Udio, or AI vocal clones. Authentic human music only.

            Respond EXCLUSIVELY with a valid raw JSON object (no markdown, no extra text):
            {
              "vibeTitle": "Short Title",
              "spokenCommentary": "Concise natural DJ voiceover",
              "searchQueries": ["Query 1", "Query 2", "Query 3", "Query 4"]
            }
            """.trimIndent()
        }
    }

    private fun parseAiJsonResponse(rawJson: String, isFrench: Boolean): BitgAiResult {
        val cleaned = cleanJsonString(rawJson)
        val element = jsonParser.parseToJsonElement(cleaned).jsonObject

        val vibeTitle = element["vibeTitle"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: (if (isFrench) "Mix Personnalisé" else "Custom Mix")
        val spoken = element["spokenCommentary"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: (if (isFrench) "C'est parti pour ta session sur mesure !" else "Let's roll with your personalized set!")
        val queries = element["searchQueries"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() }
        }.orEmpty()

        val finalQueries = if (queries.isNotEmpty()) queries else listOf("Top hits", "Chill mix")

        return BitgAiResult(
            vibeTitle = vibeTitle,
            spokenCommentary = spoken,
            searchQueries = finalQueries,
        )
    }

    private fun cleanJsonString(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```json")) s = s.removePrefix("```json")
        else if (s.startsWith("```")) s = s.removePrefix("```")
        if (s.endsWith("```")) s = s.removeSuffix("```")
        s = s.trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            s.substring(start, end + 1)
        } else {
            s
        }
    }

    // ── Google Gemini Implementation ────────────────────────────────────────

    private const val DEFAULT_GEMINI_MODEL = "gemini-2.0-flash"
    private val GEMINI_FALLBACK_MODELS = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash-lite-preview-02-05",
    )

    private class GeminiModelNotFoundException(message: String) : RuntimeException(message)

    private fun callGeminiJson(
        systemPrompt: String,
        userContent: String,
        apiKey: String,
        customModel: String,
    ): String {
        val initialModel = customModel.trim().takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
        val candidateModels = (listOf(initialModel) + GEMINI_FALLBACK_MODELS).distinct()

        var lastError: Exception? = null
        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val requestJson = buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", "$systemPrompt\n\n$userContent") })
                        }
                    })
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.7)
                    put("responseMimeType", "application/json")
                }
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val outcome = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (response.isSuccessful) {
                        val root = jsonParser.parseToJsonElement(body).jsonObject
                        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        val text = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                        return text ?: error("No content returned from Gemini: $body")
                    }
                    if (response.code == 404 || (response.code == 400 && body.contains("not found", ignoreCase = true))) {
                        throw GeminiModelNotFoundException("Gemini error ${response.code} for $model: $body")
                    }
                    error("Gemini API error ${response.code}: $body")
                }
            }

            outcome.fold(
                onSuccess = { return it },
                onFailure = { err ->
                    Log.w(TAG, "Gemini attempt on $model failed: ${err.message}")
                    lastError = err as? Exception ?: Exception(err)
                },
            )
        }
        throw lastError ?: IllegalStateException("Modèle Gemini indisponible")
    }

    private fun callGeminiRaw(testPrompt: String, apiKey: String, customModel: String): String {
        val initialModel = customModel.trim().takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
        val candidateModels = (listOf(initialModel) + GEMINI_FALLBACK_MODELS).distinct()

        var lastError: Exception? = null
        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val requestJson = buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", testPrompt) })
                        }
                    })
                }
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val outcome = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (response.isSuccessful) {
                        return "OK"
                    }
                    if (response.code == 404 || (response.code == 400 && body.contains("not found", ignoreCase = true))) {
                        throw GeminiModelNotFoundException("Gemini error ${response.code} for $model: $body")
                    }
                    error("Gemini error ${response.code}: $body")
                }
            }

            outcome.fold(
                onSuccess = { return "OK" },
                onFailure = { err ->
                    Log.w(TAG, "Gemini test on $model failed: ${err.message}")
                    lastError = err as? Exception ?: Exception(err)
                },
            )
        }
        throw lastError ?: IllegalStateException("Modèle Gemini indisponible")
    }

    // ── OpenAI & Compatible (Groq, OpenRouter, Custom) Implementation ────────

    private fun callOpenAiCompatibleJson(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userContent: String,
    ): String {
        val requestJson = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userContent)
                })
            }
            put("temperature", 0.7)
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error("AI Provider error ${response.code}: $body")
            }
            val root = jsonParser.parseToJsonElement(body).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val content = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            return content ?: error("No content in response: $body")
        }
    }

    private fun callOpenAiCompatibleRaw(
        endpoint: String,
        apiKey: String,
        model: String,
        testPrompt: String,
    ): String {
        val requestJson = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", testPrompt)
                })
            }
            put("max_tokens", 50)
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error("Error ${response.code}: $body")
            }
            return "OK"
        }
    }

    // ── Anthropic Claude Implementation ──────────────────────────────────────

    private fun callAnthropicJson(
        systemPrompt: String,
        userContent: String,
        apiKey: String,
        customModel: String,
    ): String {
        val model = customModel.ifBlank { "claude-3-5-haiku-20241022" }
        val url = "https://api.anthropic.com/v1/messages"

        val requestJson = buildJsonObject {
            put("model", model)
            put("max_tokens", 800)
            put("system", "$systemPrompt\nOutput strictly valid JSON.")
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userContent)
                })
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error("Anthropic error ${response.code}: $body")
            }
            val root = jsonParser.parseToJsonElement(body).jsonObject
            val contentList = root["content"]?.jsonArray
            val text = contentList?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            return text ?: error("No content in Anthropic response: $body")
        }
    }

    private fun callAnthropicRaw(testPrompt: String, apiKey: String, customModel: String): String {
        val model = customModel.ifBlank { "claude-3-5-haiku-20241022" }
        val url = "https://api.anthropic.com/v1/messages"

        val requestJson = buildJsonObject {
            put("model", model)
            put("max_tokens", 50)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", testPrompt)
                })
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error("Anthropic error ${response.code}: $body")
            }
            return "OK"
        }
    }
}
