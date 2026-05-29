package pl.rkarpinski.fiszkiwbiegu

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

@UnstableApi
class LearningService : MediaSessionService() {

    companion object {
        val state = MutableStateFlow(LearningState())

        const val ACTION_START = "pl.rkarpinski.fiszkiwbiegu.learning.START"
        const val ACTION_PLAY = "pl.rkarpinski.fiszkiwbiegu.learning.PLAY"
        const val ACTION_PAUSE = "pl.rkarpinski.fiszkiwbiegu.learning.PAUSE"
        const val ACTION_NEXT = "pl.rkarpinski.fiszkiwbiegu.learning.NEXT"
        const val ACTION_PREV = "pl.rkarpinski.fiszkiwbiegu.learning.PREV"
        const val ACTION_STOP = "pl.rkarpinski.fiszkiwbiegu.learning.STOP"
        const val EXTRA_FLASHCARDS_JSON = "flashcards_json"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var ttsPlayer: TtsPlayer
    private lateinit var mediaSession: MediaSession
    private var playJob: Job? = null

    private var flashcards: List<FlashcardDto> = emptyList()
    private var currentIndex = 0
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        initTts()
        val player = TtsPlayer()
        ttsPlayer = player
        player.onPlayWhenReadyChanged = { playing -> if (playing) resume() else pause() }
        player.onSeekToNext = ::next
        player.onSeekToPrevious = ::previous

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    private fun initTts() {
        tts = TextToSpeech(this) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_FLASHCARDS_JSON) ?: return START_STICKY
                flashcards = Json.decodeFromString(json)
                if (flashcards.isEmpty()) return START_NOT_STICKY
                currentIndex = 0
                isPlaying = true
                ttsPlayer.updateCurrentItem(flashcards[0].toMediaItem())
                ttsPlayer.setPlaying(true)
                startPlayJob()
            }
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> next()
            ACTION_PREV -> previous()
            ACTION_STOP -> stopSession()
        }
        return START_STICKY
    }

    private fun stopSession() {
        playJob?.cancel()
        tts?.stop()
        isPlaying = false
        flashcards = emptyList()
        ttsPlayer.setPlaying(false)
        state.value = LearningState()
    }

    private fun startPlayJob() {
        playJob?.cancel()
        playJob = serviceScope.launch {
            while (!ttsReady) delay(100)
            playLoop()
        }
    }

    private suspend fun CoroutineScope.playLoop() {
        while (isActive && flashcards.isNotEmpty()) {
            if (!isPlaying) { delay(200); continue }

            val card = flashcards[currentIndex]
            publishState(LearningPhase.SPEAKING_POLISH)
            ttsPlayer.updateCurrentItem(card.toMediaItem())

            speakAndWait(card.polishText, Locale.forLanguageTag("pl-PL"))
            if (!isActive || !isPlaying) continue

            val timeForEnglishText = speakAndWait(card.englishText, Locale.ENGLISH, 0f)
            if (!isActive || !isPlaying) continue
            delay(800)
            if (!isActive || !isPlaying) continue

            publishState(LearningPhase.SPEAKING_ENGLISH)
            repeat(3) {
                if (isActive && isPlaying) {
                    speakAndWait(card.englishText, Locale.ENGLISH)
                    if (isActive && isPlaying) delay(timeForEnglishText + 500)
                }
            }
            if (!isActive || !isPlaying) continue
            delay(1000)
            if (!isActive || !isPlaying) continue

            currentIndex = (currentIndex + 1) % flashcards.size
        }
    }

    private suspend fun speakAndWait(text: String, locale: Locale, volume: Float = 1.0f): Long =
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            val startTime = LongArray(1)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { startTime[0] = System.currentTimeMillis() }
                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(System.currentTimeMillis() - startTime[0])
                }
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(0L) }
                override fun onError(utteranceId: String?, errorCode: Int) { if (cont.isActive) cont.resume(0L) }
            })
            tts?.language = locale
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
            if (result == TextToSpeech.ERROR && cont.isActive) cont.resume(0L)
            cont.invokeOnCancellation { tts?.stop() }
        }

    private fun pause() {
        if (!isPlaying) return
        isPlaying = false
        tts?.stop()
        playJob?.cancel()
        ttsPlayer.setPlaying(false)
        publishState()
    }

    private fun resume() {
        if (flashcards.isEmpty() || isPlaying) return
        isPlaying = true
        ttsPlayer.setPlaying(true)
        startPlayJob()
    }

    private fun next() {
        if (flashcards.isEmpty()) return
        currentIndex = (currentIndex + 1) % flashcards.size
        tts?.stop()
        playJob?.cancel()
        ttsPlayer.updateCurrentItem(flashcards[currentIndex].toMediaItem())
        if (isPlaying) startPlayJob() else publishState()
    }

    private fun previous() {
        if (flashcards.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else flashcards.size - 1
        tts?.stop()
        playJob?.cancel()
        ttsPlayer.updateCurrentItem(flashcards[currentIndex].toMediaItem())
        if (isPlaying) startPlayJob() else publishState()
    }

    private fun publishState(phase: LearningPhase = LearningPhase.IDLE) {
        state.value = LearningState(
            isActive = true,
            isPlaying = isPlaying,
            flashcards = flashcards,
            currentIndex = currentIndex,
            phase = phase,
        )
    }

    override fun onDestroy() {
        playJob?.cancel()
        serviceScope.cancel()
        tts?.shutdown()
        mediaSession.release()
        ttsPlayer.release()
        state.value = LearningState()
        super.onDestroy()
    }
}

private fun FlashcardDto.toMediaItem() = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(polishText)
            .setSubtitle(englishText)
            .build()
    )
    .build()
