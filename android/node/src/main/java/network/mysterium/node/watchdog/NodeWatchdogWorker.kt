package network.mysterium.node.watchdog

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import network.mysterium.node.Storage
import network.mysterium.node.core.NodeService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic backstop that makes sure the node foreground service is actually
 * alive. The service itself updates [Storage.lastHeartbeat] on every balance
 * tick; if the heartbeat is stale, the worker restarts the service.
 *
 * Runs every 15 minutes (WorkManager's minimum periodic interval), doubling as
 * the keep-alive cron: if the service died for any reason (kill, crash, reboot
 * without receiver), it gets restarted within ~15 minutes.
 *
 * Starting a foreground service from the background relies on the user having
 * exempted the app from battery optimization (the app requests this during
 * onboarding). If the system still refuses, the failure is logged and the next
 * run simply tries again.
 */
class NodeWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val storage: Storage by inject()

    override suspend fun doWork(): Result {
        val shouldRun = storage.shouldRun
        val lastHeartbeat = storage.lastHeartbeat
        val staleFor = System.currentTimeMillis() - lastHeartbeat
        val isStale = lastHeartbeat == 0L || staleFor > STALE_THRESHOLD_MS

        if (shouldRun && isStale) {
            try {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, NodeService::class.java)
                )
            } catch (error: Exception) {
                // Android 12+ throws when a background FGS start is not allowed.
                Log.e(TAG, "unable to restart node service from watchdog", error)
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "NodeWatchdogWorker"
        val STALE_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(10)

        const val UNIQUE_WORK_NAME = "node_watchdog"
    }
}
