package network.mysterium.node

import network.mysterium.node.model.NodeConfig
import network.mysterium.node.model.NodeUsage

interface Storage {
    var isRegistered: Boolean
    var config: NodeConfig
    var usage: NodeUsage

    /**
     * User's intent: should the node keep running in the background.
     * True while the node service is (supposed to be) running,
     * set to false only on an explicit user shutdown.
     * Used by the watchdog worker and the boot receiver to avoid
     * restarting the node after the user deliberately stopped it.
     */
    var shouldRun: Boolean

    /**
     * Last time the node service reported being alive.
     * Used by the watchdog worker to detect a dead/stale service.
     */
    var lastHeartbeat: Long
}
