package network.mysterium.provider

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import network.mysterium.node.di.analyticsModule
import network.mysterium.node.di.nodeModule
import network.mysterium.node.watchdog.NodeWatchdogWorker
import network.mysterium.provider.di.deeplinkModule
import network.mysterium.provider.di.viewModels
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(
                nodeModule,
                viewModels,
                deeplinkModule,
                analyticsModule,
            )
        }
        scheduleWatchdog()
    }

    private fun scheduleWatchdog() {
        // 15-minute keep-alive cron (WorkManager's minimum periodic interval —
        // anything lower is silently clamped to 15): checks the node service
        // is alive and restarts it if the heartbeat went stale.
        val request = PeriodicWorkRequestBuilder<NodeWatchdogWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NodeWatchdogWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
