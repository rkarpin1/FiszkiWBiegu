package pl.rkarpinski.fiszkiwbiegu.di

import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.rkarpinski.fiszkiwbiegu.CollectionsViewModel
import pl.rkarpinski.fiszkiwbiegu.data.api.ApiClient
import pl.rkarpinski.fiszkiwbiegu.data.api.TokenStorage
import pl.rkarpinski.fiszkiwbiegu.data.repository.AuthRepository
import pl.rkarpinski.fiszkiwbiegu.data.repository.CollectionRepository

val appModule = module {
    single { Settings() }
    single { TokenStorage(get()) }
    single { ApiClient(get()) }
    single { AuthRepository(get(), get()) }
    single { CollectionRepository(get()) }
    viewModel { CollectionsViewModel(get()) }
}
