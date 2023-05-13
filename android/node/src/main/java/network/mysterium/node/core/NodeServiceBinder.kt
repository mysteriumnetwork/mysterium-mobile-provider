package network.mysterium.node.core

import android.os.IBinder
import mysterium.MobileNode

internal interface NodeServiceBinder : IBinder {
    val node: MobileNode?
    fun start(): MobileNode
    fun startForeground()
    fun stop()
}