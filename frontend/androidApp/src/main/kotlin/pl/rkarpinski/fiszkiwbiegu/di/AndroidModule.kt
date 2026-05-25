package pl.rkarpinski.fiszkiwbiegu.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pl.rkarpinski.fiszkiwbiegu.AndroidLearningController
import pl.rkarpinski.fiszkiwbiegu.LearningController

val androidModule = module {
    single<LearningController> { AndroidLearningController(androidContext()) }
}