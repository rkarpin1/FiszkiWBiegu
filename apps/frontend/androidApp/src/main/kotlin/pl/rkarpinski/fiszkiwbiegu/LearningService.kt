package pl.rkarpinski.fiszkiwbiegu

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat as CoreNotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.data.repository.FlashcardRepository
import pl.rkarpinski.fiszkiwbiegu.domain.Rating
import pl.rkarpinski.fiszkiwbiegu.domain.SrsCard
import pl.rkarpinski.fiszkiwbiegu.domain.SrsEngine
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningPhase
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningState
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

@UnstableApi
class LearningService : MediaSessionService() {

    companion object {
        private const val TAG = "LearningService"
        val state = MutableStateFlow(LearningState())

        const val ACTION_START = "pl.rkarpinski.fiszkiwbiegu.learning.START"
        const val ACTION_PLAY = "pl.rkarpinski.fiszkiwbiegu.learning.PLAY"
        const val ACTION_PAUSE = "pl.rkarpinski.fiszkiwbiegu.learning.PAUSE"
        const val ACTION_NEXT = "pl.rkarpinski.fiszkiwbiegu.learning.NEXT"
        const val ACTION_PREV = "pl.rkarpinski.fiszkiwbiegu.learning.PREV"
        const val ACTION_STOP = "pl.rkarpinski.fiszkiwbiegu.learning.STOP"
        const val ACTION_RATE = "pl.rkarpinski.fiszkiwbiegu.learning.RATE"
        const val ACTION_SPEED = "pl.rkarpinski.fiszkiwbiegu.learning.SPEED"
        const val EXTRA_FLASHCARDS_JSON = "flashcards_json"
        const val EXTRA_COLLECTION_JSON = "collection_json"
        const val EXTRA_RATING = "rating"
        const val EXTRA_SPEED = "speed"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "learning_session"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaPlayer: Player
    private lateinit var mediaSession: MediaSession
    private var playJob: Job? = null
    private var collectionJson: String? = null

    private val silenceUri by lazy {
        "android.resource://$packageName/${R.raw.silence}".toUri()
    }

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
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setLargeIcon(flagBitmap)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setStyle(
                    MediaStyleNotificationHelper.MediaStyle(mediaSession)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setColor(getLanguageColor(lang))
                .setColorized(true)
                .setContentIntent(mediaSession.sessionActivity)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOngoing(true)
                .addAction(
                    CoreNotificationCompat.Action(
                        android.R.drawable.ic_media_previous, "Prev",
                        actionFactory.createMediaActionPendingIntent(
                            mediaSession,
                            Player.COMMAND_SEEK_TO_PREVIOUS
                        )
                    )
                )
                .addAction(
                    CoreNotificationCompat.Action(
                        if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (player.isPlaying) "Pause" else "Play",
                        actionFactory.createMediaActionPendingIntent(
                            mediaSession,
                            Player.COMMAND_PLAY_PAUSE
                        )
                    )
                )
                .addAction(
                    CoreNotificationCompat.Action(
                        android.R.drawable.ic_media_next, "Next",
                        actionFactory.createMediaActionPendingIntent(
                            mediaSession,
                            Player.COMMAND_SEEK_TO_NEXT
                        )
                    )
                )

            return MediaNotification(NOTIFICATION_ID, builder.build())
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle
        ): Boolean = false

        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
            return MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, "Nauka")
        }
    }

    private fun getVectorBitmap(resId: Int): Bitmap? {
        val drawable: Drawable = ContextCompat.getDrawable(this, resId) ?: return null
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
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

    private val srsQueue = mutableListOf<SrsCard>()
    private var globalIndex = 0
    private var currentSrsCard: SrsCard? = null
    private var cardRated = false
    private val rng = Random.Default
    private val flashcardRepo: FlashcardRepository by inject()
    private var isPlaying = false
    private var playbackSpeed = 1.0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initTts()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus — ExoPlayer automatically acquires/releases focus
            )
            .build()
        exoPlayer.setMediaItem(MediaItem.fromUri(silenceUri))
        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        exoPlayer.prepare()

        mediaPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun play() {
                super.play()
                if (!this@LearningService.isPlaying && srsQueue.isNotEmpty()) {
                    this@LearningService.isPlaying = true
                    startPlayJob()
                }
            }

            override fun pause() {
                super.pause()
                if (this@LearningService.isPlaying) {
                    this@LearningService.isPlaying = false
                    tts?.stop()
                    playJob?.cancel()
                    publishState(card = currentSrsCard?.flashcard)
                }
            }

            override fun seekToNext() {
                rateCard(Rating.KNOW_WELL)
            }

            override fun seekToNextMediaItem() {
                rateCard(Rating.KNOW_WELL)
            }

            override fun seekToPrevious() {
                rateCard(Rating.DONT_KNOW)
            }

            override fun seekToPreviousMediaItem() {
                rateCard(Rating.DONT_KNOW)
            }

            // ExoPlayer z 1 elementem w REPEAT_MODE_ONE nie udostępnia tych komend
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
        }

        mediaSession = MediaSession.Builder(this, mediaPlayer).build()
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
                val allFlashcards: List<FlashcardDto> = Json.decodeFromString(json)
                if (allFlashcards.isEmpty()) return START_NOT_STICKY
                srsQueue.clear()
                srsQueue.addAll(SrsEngine.initQueue(allFlashcards, rng))
                globalIndex = 0
                currentSrsCard = null
                isPlaying = true
                updateCurrentItem(srsQueue[0].flashcard.toMediaItem(this))
                exoPlayer.play()
                updateSessionActivity()
                startPlayJob()
            }

            ACTION_RATE -> {
                val rating =
                    Rating.valueOf(intent.getStringExtra(EXTRA_RATING) ?: return START_STICKY)
                rateCard(rating)
            }

            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> next()
            ACTION_PREV -> previous()
            ACTION_STOP -> stopSession()
            ACTION_SPEED -> {
                playbackSpeed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                tts?.setSpeechRate(playbackSpeed)
                publishState(card = currentSrsCard?.flashcard)
            }
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

    private fun updateCurrentItem(mediaItem: MediaItem) {
        exoPlayer.replaceMediaItem(
            0,
            MediaItem.Builder()
                .setUri(silenceUri)
                .setMediaId(mediaItem.mediaId)
                .setMediaMetadata(mediaItem.mediaMetadata)
                .build()
        )
    }

    private fun stopSession() {
        playJob?.cancel()
        tts?.stop()
        isPlaying = false
        srsQueue.clear()
        currentSrsCard = null
        exoPlayer.pause()
        state.value = LearningState()
        stopSelf()
    }

    private fun startPlayJob() {
        playJob?.cancel()
        playJob = serviceScope.launch {
            val ttsOk = withTimeoutOrNull(5_000.milliseconds) {
                while (!ttsReady) delay(100.milliseconds)
            }
            if (ttsOk == null) {
                stopSession(); return@launch
            }
            playLoop()
        }
    }

    private suspend fun CoroutineScope.playLoop() {
        val collection = collectionJson?.let { Json.decodeFromString<CollectionDto>(it) }
        val srcLang = collection?.sourceLanguage ?: "pl"
        val tgtLang = collection?.targetLanguage ?: "en"
        while (isActive && srsQueue.isNotEmpty()) {
            if (!isPlaying) {
                delay(200.milliseconds); continue
            }

            val card = SrsEngine.pickNext(srsQueue, globalIndex)
            currentSrsCard = card
            cardRated = false
            globalIndex++

            publishState(LearningPhase.IDLE, card.flashcard)
            publishState(LearningPhase.SPEAKING_SOURCE, card.flashcard)
            updateCurrentItem(card.flashcard.toMediaItem(this@LearningService, srcLang))
            speakAndWait(card.flashcard.sourceText, Locale.forLanguageTag(srcLang))
            if (!isActive || !isPlaying) continue

            publishState(LearningPhase.ANSWER, card.flashcard)
            val timeForTargetText =
                speakAndWait(card.flashcard.targetText, Locale.forLanguageTag(tgtLang), 0f)
            if (!isActive || !isPlaying) continue
            delay(800.milliseconds)
            if (!isActive || !isPlaying) continue

            repeat(3) {
                if (isActive && isPlaying) {
                    publishState(LearningPhase.SPEAKING_TARGET, card.flashcard)
                    updateCurrentItem(card.flashcard.toMediaItem(this@LearningService, tgtLang))
                    speakAndWait(card.flashcard.targetText, Locale.forLanguageTag(tgtLang))
                    if (isActive && isPlaying) {
                        publishState(LearningPhase.REPEATING, card.flashcard)
                        delay((timeForTargetText + 500).milliseconds)
                    }
                }
            }
            if (!isActive || !isPlaying) continue

            if (!cardRated) {
                applyRating(card, Rating.KNOW)
            }

            publishState(LearningPhase.IDLE, card.flashcard)
            delay(1000.milliseconds)
        }
    }

    private fun applyRating(card: SrsCard, rating: Rating) {
        val now = Clock.System.now()
        val newLevel = SrsEngine.newLevel(card.srsLevel, rating)
        card.srsLevel = newLevel
        card.dueAtIndex = globalIndex + SrsEngine.intervalFor(newLevel, rating, rng)
        card.flashcard = card.flashcard.copy(srsLevel = newLevel, lastStudiedAt = now.toString())
        serviceScope.launch {
            flashcardRepo.updateSrs(card.flashcard.id, newLevel, now.toString())
        }
    }

    private fun playRatingSound(rating: Rating) {
        runCatching {
            val toneType = when (rating) {
                Rating.KNOW_WELL -> ToneGenerator.TONE_PROP_ACK
                Rating.DONT_KNOW -> ToneGenerator.TONE_PROP_NACK
                else -> return
            }
            ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(toneType, 200)
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
        exoPlayer.pause()
        publishState(card = currentSrsCard?.flashcard)
    }

    private fun resume() {
        if (srsQueue.isEmpty() || isPlaying) return
        isPlaying = true
        exoPlayer.play()
        startPlayJob()
    }

    private fun rateCard(rating: Rating) {
        val card = currentSrsCard
        if (card != null && !cardRated) {
            cardRated = true
            applyRating(card, rating)
            playRatingSound(rating)
            tts?.stop()
            playJob?.cancel()
            if (isPlaying) startPlayJob()
        }
    }

    private fun next() {
        if (srsQueue.isEmpty()) return
        globalIndex++
        tts?.stop()
        playJob?.cancel()
        if (isPlaying) startPlayJob() else publishState(card = currentSrsCard?.flashcard)
    }

    private fun previous() {
        if (srsQueue.isEmpty()) return
        tts?.stop()
        playJob?.cancel()
        if (isPlaying) startPlayJob() else publishState(card = currentSrsCard?.flashcard)
    }

    private fun publishState(
        phase: LearningPhase = LearningPhase.IDLE,
        card: FlashcardDto? = null
    ) {
        state.value = LearningState(
            isActive = true,
            isPlaying = isPlaying,
            flashcards = srsQueue.map { it.flashcard },
            currentIndex = 0,
            phase = phase,
            currentCard = card,
            playbackSpeed = playbackSpeed,
        )
    }

    override fun onDestroy() {
        playJob?.cancel()
        serviceScope.cancel()
        tts?.shutdown()
        mediaSession.release()
        exoPlayer.release()
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

    private fun FlashcardDto.toMediaItem(service: LearningService, lang: String = "pl") =
        MediaItem.Builder()
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
