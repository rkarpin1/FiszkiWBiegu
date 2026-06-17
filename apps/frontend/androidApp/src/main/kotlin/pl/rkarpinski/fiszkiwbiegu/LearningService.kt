package pl.rkarpinski.fiszkiwbiegu

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat as CoreNotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
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
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningPhase
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningState
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
        const val EXTRA_COLLECTION_JSON = "collection_json"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "learning_session"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var ttsPlayer: TtsPlayer
    private lateinit var mediaSession: MediaSession
    private var playJob: Job? = null
    private var collectionJson: String? = null

    private val notificationProvider = object : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            val player = mediaSession.player
            val metadata = player.mediaMetadata
            val lang = metadata.extras?.getString("language_code") ?: "pl"
            val title = metadata.title ?: "Nauka"
            val subtitle = metadata.subtitle ?: "Fiszki w Biegu"

            val flagBitmap = getVectorBitmap(getFlagDrawableId(lang))

            val builder = CoreNotificationCompat.Builder(this@LearningService, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now) // System monochrome icon that definitely works
                .setLargeIcon(flagBitmap)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2))
                .setColor(getLanguageColor(lang))
                .setColorized(true)
                .setContentIntent(mediaSession.sessionActivity)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .addAction(
                    CoreNotificationCompat.Action(
                        android.R.drawable.ic_media_previous, "Prev",
                        actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_SEEK_TO_PREVIOUS)
                    )
                )
                .addAction(
                    CoreNotificationCompat.Action(
                        if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (player.isPlaying) "Pause" else "Play",
                        actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_PLAY_PAUSE)
                    )
                )
                .addAction(
                    CoreNotificationCompat.Action(
                        android.R.drawable.ic_media_next, "Next",
                        actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_SEEK_TO_NEXT)
                    )
                )

            return MediaNotification(NOTIFICATION_ID, builder.build())
        }

        override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false

        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
            return MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, "Nauka")
        }
    }

    private fun getVectorBitmap(resId: Int): Bitmap? {
        val drawable: Drawable = ContextCompat.getDrawable(this, resId) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getFlagDrawableId(lang: String): Int = when (lang) {
        "en" -> R.drawable.flag_en
        "de" -> R.drawable.flag_de
        "es" -> R.drawable.flag_es
        "fr" -> R.drawable.flag_fr
        "it" -> R.drawable.flag_it
        else -> R.drawable.flag_pl
    }

    private fun getLanguageColor(lang: String): Int = when (lang) {
        "pl" -> 0xFFDC143C.toInt()
        "en" -> 0xFF012169.toInt()
        "de" -> 0xFF000000.toInt()
        "es" -> 0xFFAA151B.toInt()
        "fr" -> 0xFF002395.toInt()
        "it" -> 0xFF008C45.toInt()
        else -> 0xFF666666.toInt()
    }

    private var flashcards: List<FlashcardDto> = emptyList()
    private var currentIndex = 0
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initTts()
        val player = TtsPlayer()
        ttsPlayer = player
        player.onPlayWhenReadyChanged = { playing -> if (playing) resume() else pause() }
        player.onSeekToNext = ::next
        player.onSeekToPrevious = ::previous

        mediaSession = MediaSession.Builder(this, player)
            .build()

        setMediaNotificationProvider(notificationProvider)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nauka",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Powiadomienia o postępie nauki"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    private fun initTts() {
        tts = TextToSpeech(this) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_FLASHCARDS_JSON) ?: return START_STICKY
                collectionJson = intent.getStringExtra(EXTRA_COLLECTION_JSON)
                flashcards = Json.decodeFromString(json)
                if (flashcards.isEmpty()) return START_NOT_STICKY
                currentIndex = 0
                isPlaying = true
                ttsPlayer.updateCurrentItem(flashcards[0].toMediaItem(this))
                ttsPlayer.setPlaying(true)
                updateSessionActivity()
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

    private fun updateSessionActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_COLLECTION_JSON, collectionJson)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession.setSessionActivity(pendingIntent)
    }

    private fun stopSession() {
        playJob?.cancel()
        tts?.stop()
        isPlaying = false
        flashcards = emptyList()
        ttsPlayer.setPlaying(false)
        state.value = LearningState()
        stopSelf()
    }

    private fun startPlayJob() {
        playJob?.cancel()
        playJob = serviceScope.launch {
            while (!ttsReady) delay(100)
            playLoop()
        }
    }

    private suspend fun CoroutineScope.playLoop() {
        val collection = collectionJson?.let { Json.decodeFromString<CollectionDto>(it) }
        while (isActive && flashcards.isNotEmpty()) {
            if (!isPlaying) {
                delay(200); continue
            }

            publishState(LearningPhase.IDLE)
            val card = flashcards[currentIndex]

            publishState(LearningPhase.SPEAKING_SOURCE)
            ttsPlayer.updateCurrentItem(card.toMediaItem(this@LearningService, collection?.sourceLanguage ?: "pl"))

            speakAndWait(card.sourceText, Locale.forLanguageTag("pl-PL"))
            if (!isActive || !isPlaying) continue

            publishState(LearningPhase.ANSWER)
            val timeForTargetText = speakAndWait(card.targetText, Locale.ENGLISH, 0f)
            if (!isActive || !isPlaying) continue
            delay(800)
            if (!isActive || !isPlaying) continue

            repeat(3) {
                if (isActive && isPlaying) {
                    publishState(LearningPhase.SPEAKING_TARGET)
                    ttsPlayer.updateCurrentItem(card.toMediaItem(this@LearningService, collection?.targetLanguage ?: "en"))
                    speakAndWait(card.targetText, Locale.ENGLISH)
                    if (isActive && isPlaying) {
                        publishState(LearningPhase.REPEATING)
                        delay(timeForTargetText + 500)
                    }
                }
            }
            if (!isActive || !isPlaying) continue

            publishState(LearningPhase.IDLE)
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
                override fun onStart(utteranceId: String?) {
                    startTime[0] = System.currentTimeMillis()
                }

                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(System.currentTimeMillis() - startTime[0])
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(0L)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(0L)
                }
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
        ttsPlayer.updateCurrentItem(flashcards[currentIndex].toMediaItem(this))
        if (isPlaying) startPlayJob() else publishState()
    }

    private fun previous() {
        if (flashcards.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else flashcards.size - 1
        tts?.stop()
        playJob?.cancel()
        ttsPlayer.updateCurrentItem(flashcards[currentIndex].toMediaItem(this))
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

    private fun getFlagUri(lang: String): Uri {
        val resId = getFlagDrawableId(lang)
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(resId))
            .appendPath(resources.getResourceTypeName(resId))
            .appendPath(resources.getResourceEntryName(resId))
            .build()
    }

    private fun FlashcardDto.toMediaItem(service: LearningService, lang: String = "pl") = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(sourceText)
                .setSubtitle(targetText)
                .setArtworkUri(service.getFlagUri(lang))
                .setExtras(Bundle().apply { putString("language_code", lang) })
                .build()
        )
        .build()
}
