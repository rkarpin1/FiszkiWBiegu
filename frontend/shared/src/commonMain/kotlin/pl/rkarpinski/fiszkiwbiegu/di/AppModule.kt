package pl.rkarpinski.fiszkiwbiegu.di

import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.rkarpinski.fiszkiwbiegu.CollectionsViewModel
import pl.rkarpinski.fiszkiwbiegu.FlashcardsViewModel
import pl.rkarpinski.fiszkiwbiegu.LearningViewModel
import pl.rkarpinski.fiszkiwbiegu.data.api.ApiClient
import pl.rkarpinski.fiszkiwbiegu.data.api.TokenStorage
import pl.rkarpinski.fiszkiwbiegu.data.repository.AuthRepository
import pl.rkarpinski.fiszkiwbiegu.data.repository.CollectionRepository
import pl.rkarpinski.fiszkiwbiegu.data.repository.FlashcardRepository

val appModule = module {
    single { Settings() }
    single { TokenStorage(get()) }
    single { ApiClient(get()) }
    single { AuthRepository(get(), get()) }
    single { CollectionRepository(get()) }
    single { FlashcardRepository(get()) }
    viewModel { CollectionsViewModel(get()) }
    viewModel { params -> FlashcardsViewModel(get(), params.get()) }
    viewModel { params -> LearningViewModel(get(), get(), params.get()) }
}