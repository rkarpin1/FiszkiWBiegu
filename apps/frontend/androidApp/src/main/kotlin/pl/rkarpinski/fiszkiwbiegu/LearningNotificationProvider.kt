package pl.rkarpinski.fiszkiwbiegu

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningPhase

@UnstableApi
class LearningNotificationProvider(private val context: Context) : MediaNotification.Provider {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "learning"
    }

    init {
        val channel = NotificationChannel(CHANNEL_ID, "Tryb nauki", NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val learningState = LearningService.state.value
        val card = learningState.flashcards.getOrNull(learningState.currentIndex)
        val isEnglish = learningState.phase == LearningPhase.SPEAKING_ENGLISH

        val views = RemoteViews(context.packageName, R.layout.notification_learning).apply {
            setTextViewText(R.id.notification_title, card?.sourceText ?: "")
            setTextViewText(R.id.notification_subtitle, card?.targetText ?: "")
            setTextViewText(R.id.btn_play_pause, if (learningState.isPlaying) "⏸" else "▶")
            setInt(
                R.id.notification_root,
                "setBackgroundResource",
                if (isEnglish) R.drawable.bg_notification_english else R.drawable.bg_notification_polish,
            )
            setOnClickPendingIntent(
                R.id.btn_prev,
                actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_SEEK_TO_PREVIOUS),
            )
            setOnClickPendingIntent(
                R.id.btn_play_pause,
                actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_PLAY_PAUSE),
            )
            setOnClickPendingIntent(
                R.id.btn_next,
                actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_SEEK_TO_NEXT),
            )
        }

        val accentColor = if (isEnglish) Color.parseColor("#1A2980") else Color.parseColor("#8B0000")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(card?.sourceText ?: "FiszkiWBiegu")
            .setContentText(card?.targetText ?: "")
            .setColor(accentColor)
            .setColorized(true)
            .setCustomBigContentView(views)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        return MediaNotification(NOTIFICATION_ID, notification)
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
        return MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, "Tryb nauki")
    }

    fun buildForegroundNotification(): android.app.Notification {
        val learningState = LearningService.state.value
        val card = learningState.flashcards.getOrNull(learningState.currentIndex)
        val isEnglish = learningState.phase == LearningPhase.SPEAKING_ENGLISH
        val views = RemoteViews(context.packageName, R.layout.notification_learning).apply {
            setTextViewText(R.id.notification_title, card?.sourceText ?: "")
            setTextViewText(R.id.notification_subtitle, card?.targetText ?: "")
            setTextViewText(R.id.btn_play_pause, if (learningState.isPlaying) "⏸" else "▶")
            setInt(
                R.id.notification_root,
                "setBackgroundResource",
                if (isEnglish) R.drawable.bg_notification_english else R.drawable.bg_notification_polish,
            )
        }
        val accentColor = if (isEnglish) Color.parseColor("#1A2980") else Color.parseColor("#8B0000")
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(card?.sourceText ?: "FiszkiWBiegu")
            .setContentText(card?.targetText ?: "")
            .setColor(accentColor)
            .setColorized(true)
            .setCustomBigContentView(views)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = false
}
