package network.mysterium.node

import kotlinx.coroutines.flow.StateFlow
import network.mysterium.node.model.NodeConfig
import network.mysterium.node.model.NodeIdentity
import network.mysterium.node.model.NodeServiceType
import network.mysterium.node.model.NodeStatus
import network.mysterium.node.model.NodeTerms

interface Node {
    /**
     * Returns Node terms and conditions and terms of use content
     */
    val terms: NodeTerms

    /**
     * Returns url to NodeUI
     */
    val nodeUIUrl: String

    /**
     * Check if node is registered or not
     */
    val isRegistered: Boolean

    /**
     * Node config
     */
    val config: NodeConfig

    /**
     * Get list of current services and statuses.
     */
    val services: StateFlow<List<NodeServiceType>>

    /**
     * Get unsettled balance.
     */
    val balance: StateFlow<Double>

    /**
     * Get status of node.
     */
    val identity: StateFlow<NodeIdentity>

    /**
     * Get mobile limit reached status.
     */
    val limitMonitor: StateFlow<Boolean>

    /**
     * High-level node status (online, connecting, no network, …)
     * derived by the node service. Drives the home screen status card
     * and the notification status line.
     */
    val status: StateFlow<NodeStatus>

    /**
     * Update node config
     */
    suspend fun updateConfig(config: NodeConfig)

    /**
     * Initializes node and starts it in foreground service
     */
    suspend fun start()

    /**
     * Enable Android foreground service
     */
    suspend fun enableForegroundService()

    /**
     * Disable Android foreground service
     */
    fun disableForegroundService()

    /**
     * Cut the provider connection and re-establish it without
     * restarting the app or the foreground service.
     */
    suspend fun restartServices()

    /**
     * Stops node and foreground service
     */
    suspend fun stop()
}
