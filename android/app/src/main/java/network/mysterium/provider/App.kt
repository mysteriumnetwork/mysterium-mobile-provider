package network.mysterium.provider

import android.app.Application
import network.mysterium.provider.di.deeplinkModule
import network.mysterium.provider.di.nodeModule
import network.mysterium.provider.di.viewModels
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(
                nodeModule,
                viewModels,
                deeplinkModule,
            )
        }
    }
}
