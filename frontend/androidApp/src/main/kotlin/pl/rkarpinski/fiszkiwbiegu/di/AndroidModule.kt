package pl.rkarpinski.fiszkiwbiegu.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pl.rkarpinski.fiszkiwbiegu.AndroidLearningController
import pl.rkarpinski.fiszkiwbiegu.AndroidNetworkChecker
import pl.rkarpinski.fiszkiwbiegu.screens.learning.LearningController
import pl.rkarpinski.fiszkiwbiegu.NetworkChecker

val androidModule = module {
    single<LearningController> { AndroidLearningController(androidContext()) }
    single<NetworkChecker> { AndroidNetworkChecker(androidContext()) }
}