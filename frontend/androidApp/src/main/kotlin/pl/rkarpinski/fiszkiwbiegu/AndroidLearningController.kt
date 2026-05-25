package pl.rkarpinski.fiszkiwbiegu

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

class AndroidLearningController(private val context: Context) : LearningController {

    override val state: StateFlow<LearningState> = LearningService.state

    override fun start(flashcards: List<FlashcardDto>) {
        val intent = Intent(context, LearningService::class.java).apply {
            action = LearningService.ACTION_START
            putExtra(LearningService.EXTRA_FLASHCARDS_JSON, Json.encodeToString(flashcards))
        }
        context.startForegroundService(intent)
    }

    override fun play() = send(LearningService.ACTION_PLAY)
    override fun pause() = send(LearningService.ACTION_PAUSE)
    override fun next() = send(LearningService.ACTION_NEXT)
    override fun previous() = send(LearningService.ACTION_PREV)
    override fun stop() = send(LearningService.ACTION_STOP)

    private fun send(action: String) {
        context.startService(Intent(context, LearningService::class.java).apply { this.action = action })
    }
}