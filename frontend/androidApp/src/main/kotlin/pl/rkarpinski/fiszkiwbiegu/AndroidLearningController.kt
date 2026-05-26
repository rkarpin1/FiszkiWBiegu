package pl.rkarpinski.fiszkiwbiegu

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

@UnstableApi
class AndroidLearningController(private val context: Context) : LearningController {

    override val state: StateFlow<LearningState> = LearningService.state

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = controllerFuture?.takeIf { it.isDone && !it.isCancelled }
            ?.runCatching { get() }?.getOrNull()

    override fun start(flashcards: List<FlashcardDto>) {
        context.startForegroundService(
            Intent(context, LearningService::class.java).apply {
                action = LearningService.ACTION_START
                putExtra(LearningService.EXTRA_FLASHCARDS_JSON, Json.encodeToString(flashcards))
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
