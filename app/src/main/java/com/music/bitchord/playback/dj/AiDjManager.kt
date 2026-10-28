package com.music.bitchord.playback.dj

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import com.music.bitchord.data.isAiGenerated
import androidx.media3.session.MediaController
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.favorites.LocalFavoritesStore
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.playSongs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * BitG — Interactive AI DJ for BitChard.
 *
 * Provides real-time spoken commentary, dynamic vibe switching, user voice/prompt requests,
 * and high-fidelity native TextToSpeech engine with customizable pitch, speed, and language.
 */
object AiDjManager {
    private const val TAG = "AiDjManager"

    enum class DjVibe(val labelFr: String, val labelEn: String) {
        FAVORITES("Vos Incontournables", "Your Favorites"),
        ARTIST_SPOTLIGHT("Focus Artiste", "Artist Spotlight"),
        DISCOVERIES("Pépites & Découvertes", "Fresh Discoveries"),
        ENERGY("Vibe Énergie", "High Energy"),
        CHILL("Détente & Chill", "Chill & Relax"),
    }

    private const val TRACKS_PER_SET = 4
    private var boundController: MediaController? = null
    private var boundScope: CoroutineScope? = null
    private var boundContext: Context? = null
    private var tracksPlayedInSet = 0
    private var totalSetsCompleted = 0

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!_isActive.value) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                tracksPlayedInSet++
                Log.d(TAG, "BitG DJ track played in set: $tracksPlayedInSet / $TRACKS_PER_SET")
                if (tracksPlayedInSet >= TRACKS_PER_SET) {
                    tracksPlayedInSet = 0
                    val ctx = boundContext ?: return
                    val sc = boundScope ?: return
                    advanceToNextSet(ctx, sc)
                }
            }
        }
    }

    private fun setAudioDucking(ducked: Boolean) {
        boundController?.let { controller ->
            runCatching {
                controller.volume = if (ducked) 0.20f else 1.0f
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var currentVibeIndex = 0
    private var activeJob: Job? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentVibe = MutableStateFlow(DjVibe.FAVORITES.labelFr)
    val currentVibe: StateFlow<String> = _currentVibe.asStateFlow()

    private val _currentSpeech = MutableStateFlow("")
    val currentSpeech: StateFlow<String> = _currentSpeech.asStateFlow()

    private val _isLastResponseAi = MutableStateFlow(false)
    val isLastResponseAi: StateFlow<Boolean> = _isLastResponseAi.asStateFlow()

    private val _aiErrorNotice = MutableStateFlow<String?>(null)
    val aiErrorNotice: StateFlow<String?> = _aiErrorNotice.asStateFlow()

    data class BitgVoiceOption(
        val id: String,
        val displayName: String,
        val isNeural: Boolean,
    )

    fun init(context: Context) {
        if (tts != null && isTtsInitialized) return

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                applyTtsSettings()

                runCatching {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(attrs)
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            }
        }
    }

    fun getAvailableVoices(): List<BitgVoiceOption> {
        val ttsEngine = tts ?: return emptyList()
        val targetLocale = when (getEffectiveLanguage()) {
            "fr" -> Locale.FRENCH
            "en" -> Locale.US
            else -> {
                val sys = Locale.getDefault()
                if (sys.language == "fr") Locale.FRENCH else if (sys.language == "en") Locale.US else sys
            }
        }

        val voices = runCatching { ttsEngine.voices }.getOrNull()?.filter {
            it.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }.orEmpty()

        val sorted = voices.sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenByDescending {
                    val n = it.name.lowercase()
                    n.contains("neural") || n.contains("wavenet") || n.contains("natural") || n.contains("network")
                }
                .thenBy { it.name }
        )

        val list = mutableListOf<BitgVoiceOption>()
        list.add(BitgVoiceOption("", "Automatique (Studio HD Naturel)", true))

        sorted.forEachIndexed { index, v ->
            val n = v.name.lowercase()
            val isNeural = n.contains("neural") || n.contains("wavenet") || n.contains("natural") || n.contains("network") || v.quality >= Voice.QUALITY_HIGH
            val gender = when {
                n.contains("female") || n.contains("-f-") || n.contains("fr-fr-language") -> "Féminine"
                n.contains("male") || n.contains("-m-") -> "Masculine"
                else -> null
            }
            val qualityLabel = when {
                v.quality >= Voice.QUALITY_VERY_HIGH -> "Studio Ultra HD"
                v.quality >= Voice.QUALITY_HIGH || isNeural -> "Haute Définition HD"
                else -> "Standard"
            }
            val display = buildString {
                append("Voix ")
                append(index + 1)
                if (gender != null) append(" ($gender)")
                append(" • ")
                append(qualityLabel)
            }
            list.add(BitgVoiceOption(v.name, display, isNeural))
        }
        return list
    }

    fun applyTtsSettings() {
        val ttsEngine = tts ?: return
        val configuredLang = AppSettings.bitgLanguage.value
        val targetLocale = when (configuredLang) {
            "fr" -> Locale.FRENCH
            "en" -> Locale.US
            else -> {
                val systemLocale = Locale.getDefault()
                if (systemLocale.language == "fr") Locale.FRENCH
                else if (systemLocale.language == "en") Locale.US
                else systemLocale
            }
        }

        runCatching { ttsEngine.language = targetLocale }

        val selectedVoiceName = AppSettings.bitgVoiceName.value
        runCatching {
            val available = ttsEngine.voices?.filter {
                it.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                    !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }.orEmpty()

            val chosenVoice = if (selectedVoiceName.isNotBlank()) {
                available.firstOrNull { it.name == selectedVoiceName }
            } else null ?: run {
                // Automatically pick best studio/neural voice
                available.maxWithOrNull(
                    compareBy<Voice> { it.quality }
                        .thenBy {
                            val n = it.name.lowercase()
                            when {
                                n.contains("neural") || n.contains("wavenet") || n.contains("natural") -> 4
                                n.contains("network") -> 3
                                n.contains("high") -> 2
                                else -> 0
                            }
                        }
                )
            }

            if (chosenVoice != null) {
                ttsEngine.voice = chosenVoice
            }
        }

        // Set rich studio audio routing
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            ttsEngine.setAudioAttributes(attrs)
        }

        ttsEngine.setPitch(AppSettings.bitgVoicePitch.value)
        ttsEngine.setSpeechRate(AppSettings.bitgVoiceSpeed.value)
    }

    fun testVoice(sampleText: String? = null) {
        val isFrench = getEffectiveLanguage() == "fr"
        val text = sampleText ?: if (isFrench) {
            "Salut ! C'est BitG sur ta musique. Qu'est-ce qu'on s'écoute de bon aujourd'hui ?"
        } else {
            "Hey! It's BitG on the decks. What are we vibing to today?"
        }
        val ttsEngine = tts ?: return
        applyTtsSettings()
        val utteranceId = "preview-${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        _isSpeaking.value = true
        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun getEffectiveLanguage(): String {
        val configured = AppSettings.bitgLanguage.value
        return if (configured != "auto") configured else Locale.getDefault().language
    }

    /**
     * Starts the BitG session: announces the session, picks a themed batch,
     * speaks the voice intro with audio ducking, and queues playback.
     */
    fun start(
        context: Context,
        controller: MediaController?,
        scope: CoroutineScope,
    ) {
        init(context)
        _isActive.value = true
        tracksPlayedInSet = 0
        totalSetsCompleted = 0
        boundContext = context
        boundScope = scope
        boundController?.removeListener(playerListener)
        boundController = controller
        controller?.addListener(playerListener)
        currentVibeIndex = 0
        val vibe = DjVibe.entries[currentVibeIndex]
        updateVibeLabel(vibe)

        activeJob?.cancel()
        activeJob = scope.launch {
            val isFrench = getEffectiveLanguage() == "fr"

            if (BitgAiService.isAiConfigured()) {
                _currentVibe.value = if (isFrench) "BitG • Analyse de tes goûts..." else "BitG • Analyzing your tastes..."
                val aiResult = BitgAiService.generateAiDjSession(
                    context = context,
                    userPrompt = "Lancer une session d'ouverture exclusive basée sur l'historique d'écoute et les artistes préférés",
                    vibe = vibe,
                    isFrench = isFrench,
                ).getOrNull()

                if (aiResult != null && _isActive.value) {
                    _currentVibe.value = "BitG • ${aiResult.vibeTitle}"
                    speak(aiResult.spokenCommentary) {
                        if (!_isActive.value) return@speak
                        scope.launch {
                            val songs = searchMultipleQueries(aiResult.searchQueries)
                            if (_isActive.value && songs.isNotEmpty() && controller != null) {
                                controller.playSongs(songs, 0)
                            }
                        }
                    }
                    return@launch
                }
            }

            val songs = loadTracksForVibe(context, vibe)
            val leadSong = songs.firstOrNull()

            val text = if (isFrench) {
                if (leadSong != null) {
                    "Salut ! C'est BitG, ton DJ. Installe-toi bien, on commence avec ${leadSong.title} de ${leadSong.artist}, suivi d'une sélection sur mesure pour toi."
                } else {
                    "Salut ! C'est BitG, ton DJ. Installe-toi bien, c'est parti pour une session exclusive rien que pour toi."
                }
            } else {
                if (leadSong != null) {
                    "Hey! It's BitG, your DJ. Kicking things off with ${leadSong.title} by ${leadSong.artist}, followed by a custom mix just for you."
                } else {
                    "Hey! It's BitG, your DJ. Sit back and enjoy, let's get into your personalized mix."
                }
            }

            speak(text) {
                if (_isActive.value && songs.isNotEmpty() && controller != null) {
                    scope.launch {
                        controller.playSongs(songs, 0)
                    }
                }
            }
        }
    }

    /**
     * Interactive vibe switch: immediately introduces a new mood/batch with speech commentary.
     */
    fun switchVibe(
        context: Context,
        controller: MediaController?,
        scope: CoroutineScope,
    ) {
        init(context)
        _isActive.value = true
        tracksPlayedInSet = 0
        boundContext = context
        boundScope = scope
        if (controller != null && controller !== boundController) {
            boundController?.removeListener(playerListener)
            boundController = controller
            controller.addListener(playerListener)
        }
        currentVibeIndex = (currentVibeIndex + 1) % DjVibe.entries.size
        val vibe = DjVibe.entries[currentVibeIndex]
        updateVibeLabel(vibe)

        activeJob?.cancel()
        activeJob = scope.launch {
            val isFrench = getEffectiveLanguage() == "fr"

            if (BitgAiService.isAiConfigured()) {
                _currentVibe.value = if (isFrench) "BitG • Changement d'ambiance..." else "BitG • Switching vibe..."
                val aiResult = BitgAiService.generateAiDjSession(
                    context = context,
                    userPrompt = "Changer d'ambiance pour la vibe : ${if (isFrench) vibe.labelFr else vibe.labelEn}",
                    vibe = vibe,
                    isFrench = isFrench,
                ).getOrNull()

                if (aiResult != null && _isActive.value) {
                    _currentVibe.value = "BitG • ${aiResult.vibeTitle}"
                    speak(aiResult.spokenCommentary) {
                        if (!_isActive.value) return@speak
                        scope.launch {
                            val songs = searchMultipleQueries(aiResult.searchQueries)
                            if (_isActive.value && songs.isNotEmpty() && controller != null) {
                                controller.playSongs(songs, 0)
                            }
                        }
                    }
                    return@launch
                }
            }

            val songs = loadTracksForVibe(context, vibe)
            val leadSong = songs.firstOrNull()

            val text = when (vibe) {
                DjVibe.FAVORITES -> if (isFrench) {
                    "On revient à tes classiques incontournables. Des morceaux qui ne déçoivent jamais !"
                } else {
                    "Heading back to your all-time favorites. Tracks that never miss!"
                }
                DjVibe.ARTIST_SPOTLIGHT -> if (isFrench) {
                    if (leadSong != null) {
                        "Coup de projecteur spécial sur ${leadSong.artist} ! Découvre quelques pépites de son univers."
                    } else {
                        "Place à un focus artiste spécial. Écoute ça !"
                    }
                } else {
                    if (leadSong != null) {
                        "Artist spotlight time with ${leadSong.artist}! Here are some essential cuts."
                    } else {
                        "Time for an artist spotlight session. Check this out!"
                    }
                }
                DjVibe.DISCOVERIES -> if (isFrench) {
                    "Envie de nouveautés ? Voici quelques pépites fraîchement dénichées pour toi."
                } else {
                    "Ready for something new? Here are a few fresh recommendations lined up."
                }
                DjVibe.ENERGY -> if (isFrench) {
                    "Allez, on monte le volume et le tempo ! Place à une bonne dose d'énergie."
                } else {
                    "Turning up the tempo! Let's bring in some high energy beats."
                }
                DjVibe.CHILL -> if (isFrench) {
                    "On ralentit le rythme pour une ambiance détendue et posée. Respire et profite."
                } else {
                    "Dialing things back for a smooth, relaxed vibe. Sit back and unwind."
                }
            }

            speak(text) {
                if (_isActive.value && songs.isNotEmpty() && controller != null) {
                    scope.launch {
                        controller.playSongs(songs, 0)
                    }
                }
            }
        }
    }

    data class ParsedDjPrompt(
        val cleanSubject: String,
        val searchQuery: String,
        val spokenText: String,
    )

    fun parseUserPrompt(prompt: String, isFrench: Boolean): ParsedDjPrompt {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            return ParsedDjPrompt(
                cleanSubject = "Mix",
                searchQuery = "Top hits",
                spokenText = if (isFrench) "C'est parti pour une sélection sur mesure !" else "Let's roll with a custom mix!",
            )
        }

        var subject = trimmed

        if (isFrench) {
            // Remove conversational politeness & trailing filler
            subject = subject.replace(Regex("(?i)\\b(s'il te plaît|s'il vous plaît|svp|merci|stp)\\b"), "").trim()

            // Remove leading trigger phrases
            val frTriggers = listOf(
                Regex("^(est-ce que tu peux( me)?|peux-tu( me)?|peux tu( me)?|tu peux( me)?)\\s+(mettre|lancer|balancer|jouer|passer)\\s+", RegexOption.IGNORE_CASE),
                Regex("^(je voudrais( bien)?|j'aimerais( bien)?|je veux( bien)?|j'ai envie d'écouter|j'ai envie de|je souhaite)\\s+", RegexOption.IGNORE_CASE),
                Regex("^(mets-moi|mets moi|mets-nous|mets nous|mets|balance-moi|balance moi|balance|lance-moi|lance moi|lance|joue-moi|joue moi|joue|fais péter|fais peter|fais tourner|passe-moi|passe moi|passe|écoute|écouter)\\s+", RegexOption.IGNORE_CASE),
            )
            for (trigger in frTriggers) {
                subject = trigger.replace(subject, "").trim()
            }

            // Remove leading partitives / articles
            val frArticles = Regex("^(du |de la |de l'|des |d'|un peu de |le |la |les |l')", RegexOption.IGNORE_CASE)
            val withoutArticle = frArticles.replace(subject, "").trim()
            if (withoutArticle.isNotBlank()) {
                subject = withoutArticle
            }

            subject = subject.trimEnd('.', '!', '?', ',', ';').trim()
            val displaySubject = subject.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            val frPhrases = listOf(
                "Ça roule ! Je te balance du $displaySubject, monte le son !",
                "C'est parti pour une session $displaySubject, installe-toi bien !",
                "Reçu 5 sur 5 ! Place à $displaySubject tout de suite.",
                "Excellente idée ! Je te cale du $displaySubject, fais péter les watts !",
                "Et voici du $displaySubject rien que pour toi, bonne écoute !",
                "Tout de suite dans les oreilles : du bon $displaySubject, monte le volume !"
            )
            val spoken = frPhrases.random()

            return ParsedDjPrompt(
                cleanSubject = displaySubject,
                searchQuery = displaySubject,
                spokenText = spoken,
            )
        } else {
            subject = subject.replace(Regex("(?i)\\b(please|thanks|thank you)\\b"), "").trim()

            val enTriggers = listOf(
                Regex("^(could you( please)?|can you( please)?|would you( please)?)\\s+(play|put on|spin|blast)\\s+", RegexOption.IGNORE_CASE),
                Regex("^(i'd like( to hear| some)?|i want( to listen to| to hear| some)?|i feel like listening to)\\s+", RegexOption.IGNORE_CASE),
                Regex("^(play( me)?|put on|blast|spin|give me)\\s+", RegexOption.IGNORE_CASE),
            )
            for (trigger in enTriggers) {
                subject = trigger.replace(subject, "").trim()
            }

            val enArticles = Regex("^(some |the |a |an )", RegexOption.IGNORE_CASE)
            val withoutArticle = enArticles.replace(subject, "").trim()
            if (withoutArticle.isNotBlank()) {
                subject = withoutArticle
            }

            subject = subject.trimEnd('.', '!', '?', ',', ';').trim()
            val displaySubject = subject.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            val enPhrases = listOf(
                "You got it! Spinning some $displaySubject for you right now, turn it up!",
                "Great choice! Here comes a fresh $displaySubject session, enjoy!",
                "Say no more! Queueing up $displaySubject, let's ride!",
                "Right on! Dropping some $displaySubject into the mix for you!"
            )
            val spoken = enPhrases.random()

            return ParsedDjPrompt(
                cleanSubject = displaySubject,
                searchQuery = displaySubject,
                spokenText = spoken,
            )
        }
    }

    /**
     * Ask BitG anything! (Voice or text prompt request like Spotify AI DJ).
     */
    fun askBitG(
        context: Context,
        prompt: String,
        controller: MediaController?,
        scope: CoroutineScope,
    ) {
        val rawPrompt = prompt.trim()
        if (rawPrompt.isBlank()) return
        init(context)
        _isActive.value = true
        tracksPlayedInSet = 0
        boundContext = context
        boundScope = scope
        if (controller != null && controller !== boundController) {
            boundController?.removeListener(playerListener)
            boundController = controller
            controller.addListener(playerListener)
        }

        val isFrench = getEffectiveLanguage() == "fr"

        activeJob?.cancel()
        activeJob = scope.launch {
            if (BitgAiService.isAiConfigured()) {
                _currentVibe.value = if (isFrench) "BitG • Réflexion IA..." else "BitG • AI Thinking..."
                val aiOutcome = BitgAiService.generateAiDjSession(
                    context = context,
                    userPrompt = rawPrompt,
                    isFrench = isFrench,
                )
                val aiResult = aiOutcome.getOrNull()

                if (aiResult != null && _isActive.value) {
                    _isLastResponseAi.value = true
                    _aiErrorNotice.value = null
                    _currentVibe.value = "BitG • ${aiResult.vibeTitle}"
                    speak(aiResult.spokenCommentary) {
                        if (!_isActive.value) return@speak
                        scope.launch {
                            val songs = searchMultipleQueries(aiResult.searchQueries)
                            if (_isActive.value && songs.isNotEmpty() && controller != null) {
                                controller.playSongs(songs, 0)
                            }
                        }
                    }
                    return@launch
                } else if (aiOutcome.isFailure) {
                    val err = aiOutcome.exceptionOrNull()?.message ?: "Erreur IA"
                    Log.w(TAG, "AI Session failed: $err")
                    _aiErrorNotice.value = err
                }
            }

            _isLastResponseAi.value = false
            val parsed = parseUserPrompt(rawPrompt, isFrench)
            _currentVibe.value = "BitG • ${parsed.cleanSubject}"
            speak(parsed.spokenText) {
                if (!_isActive.value) return@speak
                scope.launch {
                    val songs = searchForPrompt(parsed.searchQuery)
                    if (_isActive.value && songs.isNotEmpty() && controller != null) {
                        controller.playSongs(songs, 0)
                    }
                }
            }
        }
    }

    private suspend fun searchMultipleQueries(queries: List<String>): List<Song> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Song>()
        val seenIds = mutableSetOf<String>()

        for (query in queries) {
            val tracks = runCatching {
                YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()?.mapNotNull {
                    when (it) {
                        is SearchResult.Track -> it.song
                        is SearchResult.TopTrack -> it.song
                        else -> null
                    }
                }
            }.getOrNull().orEmpty()

            tracks.take(3).forEach { song ->
                if (seenIds.add(song.videoId)) {
                    if (!AppSettings.blockAiContent.value || !song.isAiGenerated()) {
                        out.add(song)
                    }
                }
            }

            if (out.size >= 20) break
        }

        if (out.isEmpty() && queries.isNotEmpty()) {
            return@withContext searchForPrompt(queries.first())
        }

        out
    }

    private suspend fun searchForPrompt(query: String): List<Song> = withContext(Dispatchers.IO) {
        val tracks = runCatching {
            YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()?.mapNotNull {
                when (it) {
                    is SearchResult.Track -> it.song
                    is SearchResult.TopTrack -> it.song
                    else -> null
                }
            }
        }.getOrNull().orEmpty()

        if (tracks.isNotEmpty()) {
            return@withContext tracks.take(15)
        }

        val videos = runCatching {
            YtMusicRepository.search(query, SearchFilter.VIDEOS).getOrNull()?.mapNotNull {
                when (it) {
                    is SearchResult.Track -> it.song
                    is SearchResult.TopTrack -> it.song
                    else -> null
                }
            }
        }.getOrNull().orEmpty()

        if (videos.isNotEmpty()) {
            return@withContext videos.take(15)
        }

        fetchExploreSongs()
    }

    /**
     * Stops the BitG DJ session cleanly: cancels active background generation jobs,
     * immediately halts any active speech synthesis, resets states, and optionally pauses music.
     */
    fun stop(controller: MediaController? = null, pauseMusic: Boolean = false) {
        activeJob?.cancel()
        activeJob = null
        _isActive.value = false
        _isSpeaking.value = false
        _currentSpeech.value = ""
        tracksPlayedInSet = 0
        setAudioDucking(false)
        (controller ?: boundController)?.removeListener(playerListener)
        boundController = null
        boundScope = null
        boundContext = null
        runCatching { tts?.stop() }
        if (pauseMusic && controller != null) {
            runCatching { controller.pause() }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!_isActive.value) {
            _isSpeaking.value = false
            return
        }

        _currentSpeech.value = text

        if (!AppSettings.bitgVoiceEnabled.value) {
            _isSpeaking.value = false
            onDone?.invoke()
            return
        }

        val ttsEngine = tts
        if (ttsEngine == null || !isTtsInitialized) {
            _isSpeaking.value = false
            onDone?.invoke()
            return
        }

        applyTtsSettings()

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        setAudioDucking(true)

        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (_isActive.value) _isSpeaking.value = true
                setAudioDucking(true)
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    _isSpeaking.value = false
                    setAudioDucking(false)
                    if (_isActive.value) onDone?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    _isSpeaking.value = false
                    setAudioDucking(false)
                    if (_isActive.value) onDone?.invoke()
                }
            }
        })

        _isSpeaking.value = true
        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * Continuous Spotify AI DJ inter-set bridge:
     * When a set finishes (every 4 songs), automatically switches to the next thematic vibe,
     * speaks contextual inter-set transition commentary, and plays the new curated set.
     */
    private fun advanceToNextSet(context: Context, scope: CoroutineScope) {
        if (!_isActive.value) return
        activeJob?.cancel()
        activeJob = scope.launch {
            totalSetsCompleted++
            currentVibeIndex = (currentVibeIndex + 1) % DjVibe.entries.size
            val nextVibe = DjVibe.entries[currentVibeIndex]
            updateVibeLabel(nextVibe)

            val isFrench = getEffectiveLanguage() == "fr"

            if (BitgAiService.isAiConfigured()) {
                val transitionPrompt = if (isFrench) {
                    "Transition fluide entre deux blocs musicaux de DJ. Fais un commentaire court et annonce le prochain set ambiance : ${nextVibe.labelFr}"
                } else {
                    "Smooth transition between two DJ sets. Keep it punchy and announce the upcoming set vibe: ${nextVibe.labelEn}"
                }
                val aiResult = BitgAiService.generateAiDjSession(
                    context = context,
                    userPrompt = transitionPrompt,
                    vibe = nextVibe,
                    isFrench = isFrench,
                ).getOrNull()

                if (aiResult != null && _isActive.value) {
                    _currentVibe.value = "BitG • ${aiResult.vibeTitle}"
                    speak(aiResult.spokenCommentary) {
                        if (!_isActive.value) return@speak
                        scope.launch {
                            val rawSongs = searchMultipleQueries(aiResult.searchQueries)
                            val songs = if (AppSettings.blockAiContent.value) rawSongs.filterNot { it.isAiGenerated() } else rawSongs
                            val ctrl = boundController
                            if (_isActive.value && songs.isNotEmpty() && ctrl != null) {
                                ctrl.playSongs(songs, 0)
                            }
                        }
                    }
                    return@launch
                }
            }

            val songs = loadTracksForVibe(context, nextVibe).let { list ->
                if (AppSettings.blockAiContent.value) list.filterNot { it.isAiGenerated() } else list
            }
            val leadSong = songs.firstOrNull()

            val text = if (isFrench) {
                if (leadSong != null) {
                    "Voilà pour ce bloc ! On change d'ambiance avec ${nextVibe.labelFr}, en commençant par ${leadSong.title} de ${leadSong.artist}."
                } else {
                    "On continue sur notre lancée ! Place à un nouveau set : ${nextVibe.labelFr}."
                }
            } else {
                if (leadSong != null) {
                    "That wraps up that run! Shifting gears into ${nextVibe.labelEn}, starting off with ${leadSong.title} by ${leadSong.artist}."
                } else {
                    "Keeping the energy flowing! Moving into ${nextVibe.labelEn}."
                }
            }

            speak(text) {
                val ctrl = boundController
                if (_isActive.value && songs.isNotEmpty() && ctrl != null) {
                    scope.launch {
                        ctrl.playSongs(songs, 0)
                    }
                }
            }
        }
    }

    private fun updateVibeLabel(vibe: DjVibe) {
        val isFrench = getEffectiveLanguage() == "fr"
        _currentVibe.value = if (isFrench) vibe.labelFr else vibe.labelEn
    }

    private fun com.music.bitchord.data.model.ShelfItem.toSong(): Song? {
        val id = videoId ?: return null
        return Song(videoId = id, title = title, artist = subtitle, thumbnailUrl = thumbnailUrl)
    }

    private suspend fun loadTracksForVibe(context: Context, vibe: DjVibe): List<Song> = withContext(Dispatchers.IO) {
        val favorites: List<Song> = runCatching { LocalFavoritesStore.getFavorites() }.getOrDefault(emptyList())
        val downloaded: List<Song> = runCatching { Downloads.getDownloadedSongs(context) }.getOrDefault(emptyList())
        val local: List<Song> = runCatching { LocalMediaRepository.getLocalMusic(context) }.getOrDefault(emptyList())
        val recents: List<Song> = runCatching {
            YtMusicRepository.homeRecentlyPlayed().getOrNull()?.items?.mapNotNull { it.toSong() }
        }.getOrNull().orEmpty()

        val basePool: List<Song> = (favorites + recents + downloaded + local).distinctBy { it.videoId }
        val seedPool: List<Song> = if (AppSettings.blockAiContent.value) {
            basePool.filterNot { it.isAiGenerated() }
        } else {
            basePool
        }

        when (vibe) {
            DjVibe.FAVORITES -> {
                if (seedPool.isNotEmpty()) {
                    seedPool.shuffled().take(6)
                } else {
                    fetchExploreSongs()
                }
            }
            DjVibe.ARTIST_SPOTLIGHT -> {
                val artist = seedPool.mapNotNull { it.artist.takeIf { a -> a.isNotBlank() } }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                if (artist != null) {
                    val artistTracks = seedPool.filter { it.artist.equals(artist, ignoreCase = true) }
                    if (artistTracks.size >= 3) {
                        artistTracks.take(6)
                    } else {
                        val seed = artistTracks.firstOrNull() ?: seedPool.firstOrNull()
                        if (seed != null) {
                            val radio = YtMusicRepository.radio(seed.videoId).getOrNull().orEmpty()
                            if (radio.isNotEmpty()) radio.take(6) else seedPool.take(6)
                        } else {
                            fetchExploreSongs()
                        }
                    }
                } else {
                    seedPool.shuffled().take(6)
                }
            }
            DjVibe.DISCOVERIES -> {
                val seed = seedPool.shuffled().firstOrNull()
                if (seed != null) {
                    val radio = YtMusicRepository.radio(seed.videoId).getOrNull().orEmpty()
                    if (radio.isNotEmpty()) radio.shuffled().take(6) else fetchExploreSongs()
                } else {
                    fetchExploreSongs()
                }
            }
            DjVibe.ENERGY -> {
                val pool = if (seedPool.isNotEmpty()) seedPool.shuffled() else fetchExploreSongs()
                pool.take(6)
            }
            DjVibe.CHILL -> {
                val pool = if (seedPool.isNotEmpty()) seedPool.reversed() else fetchExploreSongs()
                pool.take(6)
            }
        }
    }

    private suspend fun fetchExploreSongs(): List<Song> = withContext(Dispatchers.IO) {
        val home = YtMusicRepository.home().getOrNull()
        val songs: List<Song> = home?.shelves?.flatMap { shelf -> shelf.items.mapNotNull { it.toSong() } }.orEmpty()
        if (songs.isNotEmpty()) songs.shuffled().take(6) else emptyList()
    }
}
