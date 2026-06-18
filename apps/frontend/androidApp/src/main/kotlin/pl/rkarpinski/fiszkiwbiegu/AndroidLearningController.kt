package pl.rkarpinski.fiszkiwbiegu

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.domain.Rating
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningController
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningState

@UnstableApi
class AndroidLearningController(private val context: Context) : LearningController {

    override val state: StateFlow<LearningState> = LearningService.state

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = controllerFuture?.takeIf { it.isDone && !it.isCancelled }
            ?.runCatching { get() }?.getOrNull()

    override fun start(collection: CollectionDto, flashcards: List<FlashcardDto>) {
        context.startForegroundService(
            Intent(context, LearningService::class.java).apply {
                action = LearningService.ACTION_START
                putExtra(LearningService.EXTRA_FLASHCARDS_JSON, Json.encodeToString(flashcards))
                putExtra(LearningService.EXTRA_COLLECTION_JSON, Json.encodeToString(collection))
            }
        )
        connectController()
    }

    private fun connectController() {
        releaseController()
        val token = SessionToken(context, ComponentName(context, LearningService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
    }

    override fun play() { controller?.play() }
    override fun pause() { controller?.pause() }
    override fun next() { controller?.seekToNext() }
    override fun previous() { controller?.seekToPrevious() }

    override fun rate(rating: Rating) {
        context.startService(
            Intent(context, LearningService::class.java).apply {
                action = LearningService.ACTION_RATE
                putExtra(LearningService.EXTRA_RATING, rating.name)
            }
        )
    }

    override fun setSpeed(speed: Float) {
        context.startService(
            Intent(context, LearningService::class.java).apply {
                action = LearningService.ACTION_SPEED
                putExtra(LearningService.EXTRA_SPEED, speed)
            }
        )
    }

    override fun stop() {
        releaseController()
        context.startService(
            Intent(context, LearningService::class.java).apply { action = LearningService.ACTION_STOP }
        )
    }

    private fun releaseController() {
        controllerFuture?.let { future ->
            if (future.isDone && !future.isCancelled) {
                runCatching { future.get().release() }
            } else {
                future.cancel(true)
            }
        }
        controllerFuture = null
    }
}
