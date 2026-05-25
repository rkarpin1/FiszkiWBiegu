package pl.rkarpinski.fiszkiwbiegu

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pl.rkarpinski.fiszkiwbiegu.di.androidModule
import pl.rkarpinski.fiszkiwbiegu.di.appModule

class FiszkiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FiszkiApplication)
            modules(appModule, androidModule)
        }
    }
}