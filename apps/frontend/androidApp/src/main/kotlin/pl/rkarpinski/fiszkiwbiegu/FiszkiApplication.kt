package pl.rkarpinski.fiszkiwbiegu

import android.app.Application
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
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

    // Called by emulators and tests; not guaranteed on physical devices (process is killed).
    override fun onTerminate() {
        getKoin().get<NetworkChecker>().release()
        stopKoin()
        super.onTerminate()
    }
}