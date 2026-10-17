package com.music.bitchord.playback.dj

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import java.util.Locale
import java.util.UUID

/**
 * BitG — Interactive AI DJ for BitChard.
 *
 * Provides real-time spoken commentary, dynamic vibe switching, user voice/prompt requests,
 * and high-fidelity native TextToSpeech engine with customizable pitch, speed, and language.
 */
object AiDjManager {

    enum class DjVibe(val labelFr: String, val labelEn: String) {
        FAVORITES("Vos Incontournables", "Your Favorites"),
        ARTIST_SPOTLIGHT("Focus Artiste", "Artist Spotlight"),
        DISCOVERIES("Pépites & Découvertes", "Fresh Discoveries"),
        ENERGY("Vibe Énergie", "High Energy"),
        CHILL("Détente & Chill", "Chill & Relax"),
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

    fun init(context: Context) {
        if (tts != null && isTtsInitialized) return

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                applyTtsSettings()

                runCatching {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
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

    private fun applyTtsSettings() {
        val ttsEngine = tts ?: return
        val configuredLang = AppSettings.bitgLanguage.value
        when (configuredLang) {
            "fr" -> ttsEngine.setLanguage(Locale.FRENCH)
            "en" -> ttsEngine.setLanguage(Locale.US)
            else -> {
                val systemLocale = Locale.getDefault()
                val langResult = ttsEngine.setLanguage(systemLocale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    if (systemLocale.language == "fr") {
                        ttsEngine.setLanguage(Locale.FRENCH)
                    } else {
                        ttsEngine.setLanguage(Locale.US)
                    }
                }
            }
        }
        ttsEngine.setPitch(AppSettings.bitgVoicePitch.value)
        ttsEngine.setSpeechRate(AppSettings.bitgVoiceSpeed.value)
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
        currentVibeIndex = 0
        val vibe = DjVibe.entries[currentVibeIndex]
        updateVibeLabel(vibe)

        activeJob?.cancel()
        activeJob = scope.launch {
            val songs = loadTracksForVibe(context, vibe)
            val isFrench = getEffectiveLanguage() == "fr"
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
        currentVibeIndex = (currentVibeIndex + 1) % DjVibe.entries.size
        val vibe = DjVibe.entries[currentVibeIndex]
        updateVibeLabel(vibe)

        activeJob?.cancel()
        activeJob = scope.launch {
            val songs = loadTracksForVibe(context, vibe)
            val isFrench = getEffectiveLanguage() == "fr"
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

    /**
     * Ask BitG anything! (Voice or text prompt request like Spotify AI DJ).
     */
    fun askBitG(
        context: Context,
        prompt: String,
        controller: MediaController?,
        scope: CoroutineScope,
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return
        init(context)
        _isActive.value = true
        _currentVibe.value = "BitG : $cleanPrompt"

        activeJob?.cancel()
        activeJob = scope.launch {
            val isFrench = getEffectiveLanguage() == "fr"
            val text = if (isFrench) {
                "C'est noté ! Je te lance une sélection $cleanPrompt, monte le son !"
            } else {
                "On it! Cooking up a $cleanPrompt session for you. Enjoy!"
            }

            speak(text) {
                if (!_isActive.value) return@speak
                scope.launch {
                    val songs = searchForPrompt(cleanPrompt)
                    if (_isActive.value && songs.isNotEmpty() && controller != null) {
                        controller.playSongs(songs, 0)
                    }
                }
            }
        }
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

        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (_isActive.value) _isSpeaking.value = true
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    _isSpeaking.value = false
                    if (_isActive.value) onDone?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    _isSpeaking.value = false
                    if (_isActive.value) onDone?.invoke()
                }
            }
        })

        _isSpeaking.value = true
        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
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

        val seedPool: List<Song> = (favorites + recents + downloaded + local).distinctBy { it.videoId }

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
