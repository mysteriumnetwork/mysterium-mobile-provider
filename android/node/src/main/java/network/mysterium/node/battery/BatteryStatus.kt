package network.mysterium.node.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BatteryStatus(context: Context) {

    val isCharging: StateFlow<Boolean>
        get() = isChargingFlow.asStateFlow()

    private val isChargingFlow = MutableStateFlow(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isChargingFlow.update { intent.action == Intent.ACTION_POWER_CONNECTED }
        }
    }

    init {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        context.registerReceiver(receiver, filter)
    }
}