package pl.rkarpinski.fiszkiwbiegu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
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

class LearningService : Service() {

    companion object {
        val state = MutableStateFlow(LearningState())

        const val ACTION_START = "pl.rkarpinski.fiszkiwbiegu.learning.START"
        const val ACTION_PLAY = "pl.rkarpinski.fiszkiwbiegu.learning.PLAY"
        const val ACTION_PAUSE = "pl.rkarpinski.fiszkiwbiegu.learning.PAUSE"
        const val ACTION_NEXT = "pl.rkarpinski.fiszkiwbiegu.learning.NEXT"
        const val ACTION_PREV = "pl.rkarpinski.fiszkiwbiegu.learning.PREV"
        const val ACTION_STOP = "pl.rkarpinski.fiszkiwbiegu.learning.STOP"
        const val EXTRA_FLASHCARDS_JSON = "flashcards_json"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "learning"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaSession: MediaSession? = null
    private var playJob: Job? = null

    private var flashcards: List<FlashcardDto> = emptyList()
    private var currentIndex = 0
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initTts()
        initMediaSession()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Tryb nauki", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "FiszkiWBiegu").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onStop() = stopSelf()
            })
            isActive = true
        }
        updatePlaybackState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_FLASHCARDS_JSON) ?: return START_STICKY
                flashcards = Json.decodeFromString(json)
                currentIndex = 0
                isPlaying = true
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                startPlayJob()
            }
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> next()
            ACTION_PREV -> previous()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startPlayJob() {
        playJob?.cancel()
        playJob = serviceScope.launch {
            while (!ttsReady) delay(100)
            playLoop()
        }
    }

    private suspend fun playLoop() {
        while (isActive && flashcards.isNotEmpty()) {
            if (!isPlaying) { delay(200); continue }

            val card = flashcards[currentIndex]
            publishState(LearningPhase.SPEAKING_POLISH)
            updateNotification()

            speakAndWait(card.polishText, Locale("pl", "PL"))
            if (!isActive || !isPlaying) continue
            delay(800)
            if (!isActive || !isPlaying) continue

            publishState(LearningPhase.SPEAKING_ENGLISH)
            repeat(3) { i ->
                if (isActive && isPlaying) {
                    speakAndWait(card.englishText, Locale.ENGLISH)
                    if (i < 2 && isActive && isPlaying) delay(500)
                }
            }
            if (!isActive || !isPlaying) continue
            delay(1000)
            if (!isActive || !isPlaying) continue

            currentIndex = (currentIndex + 1) % flashcards.size
        }
    }

    private suspend fun speakAndWait(text: String, locale: Locale) =
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
            })
            tts?.language = locale
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result == TextToSpeech.ERROR && cont.isActive) cont.resume(Unit)
            cont.invokeOnCancellation { tts?.stop() }
        }

    private fun pause() {
        isPlaying = false
        tts?.stop()
        playJob?.cancel()
        publishState()
        updatePlaybackState()
        updateNotification()
    }

    private fun resume() {
        if (flashcards.isEmpty()) return
        isPlaying = true
        startPlayJob()
        updatePlaybackState()
        updateNotification()
    }

    private fun next() {
        if (flashcards.isEmpty()) return
        currentIndex = (currentIndex + 1) % flashcards.size
        tts?.stop()
        playJob?.cancel()
        if (isPlaying) startPlayJob() else publishState()
    }

    private fun previous() {
        if (flashcards.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else flashcards.size - 1
        tts?.stop()
        playJob?.cancel()
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

    private fun updatePlaybackState() {
        val playState = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_STOP,
                )
                .setState(playState, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val card = flashcards.getOrNull(currentIndex)
        val contentText = card?.let { "${it.polishText} → ${it.englishText}" } ?: "Ładowanie..."
        val playPauseAction = if (isPlaying)
            NotificationCompat.Action(0, "⏸", pendingIntent(ACTION_PAUSE))
        else
            NotificationCompat.Action(0, "▶", pendingIntent(ACTION_PLAY))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("FiszkiWBiegu – Tryb nauki")
            .setContentText(contentText)
            .addAction(NotificationCompat.Action(0, "⏮", pendingIntent(ACTION_PREV)))
            .addAction(playPauseAction)
            .addAction(NotificationCompat.Action(0, "⏭", pendingIntent(ACTION_NEXT)))
            .addAction(NotificationCompat.Action(0, "✕ Stop", pendingIntent(ACTION_STOP)))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun pendingIntent(action: String): PendingIntent {
        val intent = Intent(this, LearningService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onDestroy() {
        playJob?.cancel()
        serviceScope.cancel()
        tts?.shutdown()
        mediaSession?.apply { isActive = false; release() }
        state.value = LearningState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}