package network.mysterium.provider.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import network.mysterium.node.Storage
import network.mysterium.node.core.NodeService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Restarts the node foreground service after the device reboots or after the
 * app itself is updated (e.g. sideloading a new build) — but only if the node
 * was running before (i.e. the user did not explicitly shut it down).
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val storage: Storage by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!storage.shouldRun) return

        // This is a background start; the battery-optimization exemption
        // (requested during onboarding) allows it. Never crash the receiver if
        // the system refuses — the watchdog worker retries later.
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NodeService::class.java)
            )
        } catch (error: Exception) {
            Log.e(TAG, "unable to start node service on $action", error)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
