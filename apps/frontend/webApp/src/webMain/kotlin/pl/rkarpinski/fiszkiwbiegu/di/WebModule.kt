package pl.rkarpinski.fiszkiwbiegu.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.dsl.module
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.domain.Rating
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningController
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningState

private object NoOpLearningController : LearningController {
    override val state: StateFlow<LearningState> = MutableStateFlow(LearningState())
    override fun start(collection: CollectionDto, flashcards: List<FlashcardDto>) {}
    override fun play() {}
    override fun pause() {}
    override fun next() {}
    override fun previous() {}
    override fun stop() {}
    override fun rate(rating: Rating) {}
    override fun setSpeed(speed: Float) {}
}

val webModule = module {
    single<LearningController> { NoOpLearningController }
}
