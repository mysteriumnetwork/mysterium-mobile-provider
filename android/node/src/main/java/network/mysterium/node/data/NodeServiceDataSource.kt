package network.mysterium.node.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import mysterium.GetBalanceRequest
import mysterium.GetIdentityRequest
import mysterium.MobileNode
import network.mysterium.node.Storage
import network.mysterium.node.analytics.NodeAnalytics
import network.mysterium.node.analytics.event.AnalyticsEvent
import network.mysterium.node.core.NodeContainer
import network.mysterium.node.model.NodeIdentity
import network.mysterium.node.model.NodeServiceType
import network.mysterium.node.model.NodeStatus
import network.mysterium.node.network.NetworkReporter
import network.mysterium.node.network.NetworkType

interface NodeServiceDataSource {

    val identity: StateFlow<NodeIdentity>
    val services: StateFlow<List<NodeServiceType>>
    val balance: StateFlow<Double>
    val limitMonitor: StateFlow<Boolean>
    val status: StateFlow<NodeStatus>

    suspend fun fetchIdentity()
    suspend fun fetchBalance()
    suspend fun fetchServices()
    suspend fun updateMobileDataUsage(usedBytesPerMonth: Long)
    fun updateStatus(status: NodeStatus)
}

class NodeServiceDataSourceImpl(
    private val nodeContainer: NodeContainer,
    private val storage: Storage,
    private val networkReporter: NetworkReporter,
    private val analytics: NodeAnalytics,
) : NodeServiceDataSource {

    private val TAG: String = NodeServiceDataSource::class.java.simpleName

    // Tolerate new fields from newer node versions instead of failing the
    // whole fetch (the node AAR is bumped independently of this app code).
    private val json = Json { ignoreUnknownKeys = true }

    override val identity: MutableStateFlow<NodeIdentity> = MutableStateFlow(NodeIdentity.empty())
    override val services: MutableStateFlow<List<NodeServiceType>> = MutableStateFlow(emptyList())
    override val balance: MutableStateFlow<Double> = MutableStateFlow(0.0)
    override val limitMonitor: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val status: MutableStateFlow<NodeStatus> = MutableStateFlow(NodeStatus.OFFLINE)

    override suspend fun fetchIdentity() {
        val mobileNode: MobileNode = nodeContainer.getInstance()
        val response = mobileNode.getIdentity(GetIdentityRequest())
        identity.update {
            NodeIdentity(
                response.identityAddress,
                response.channelAddress,
                NodeIdentity.Status.from(response.registrationStatus)
            )
        }
    }

    override suspend fun fetchBalance() {
        val mobileNode: MobileNode = nodeContainer.getInstance()
        val request = GetBalanceRequest()
        request.identityAddress = identity.value.address
        val response = mobileNode.getUnsettledEarnings(request)
        balance.update {
            response.balance
        }
    }


    override suspend fun fetchServices() {
        val mobileNode: MobileNode = nodeContainer.getInstance()
        val raw = mobileNode.allServicesState.decodeToString()
        val newServices = json.decodeFromString<List<NodeServiceType>>(raw).toSet().toList()
        val oldServices = services.value
        // Report only the services whose state actually changed (the previous
        // all{} check was only ever true for 0-1 known services).
        newServices
            .filter { new -> oldServices.any { old -> new.id == old.id && new.state != old.state } }
            .forEach { changed ->
                analytics.trackEvent(
                    AnalyticsEvent.ServicesStatusEvent(
                        serviceName = changed.id.name,
                        status = changed.state.name
                    )
                )
            }
        services.value = newServices
    }

    override suspend fun updateMobileDataUsage(usedBytesPerMonth: Long) {
        val mobileNode: MobileNode = nodeContainer.getInstance()

        val config = storage.config
        if (config.useMobileData &&
            config.useMobileDataLimit &&
            config.mobileDataLimit != null &&
            networkReporter.isConnected(NetworkType.MOBILE)
        ) {
            var usage = storage.usage
            usage = usage.copy(bytes = usedBytesPerMonth)
            storage.usage = usage
            if (usage.bytes > config.mobileDataLimit) {
                limitMonitor.update { true }
                mobileNode.stopProvider()
            } else {
                limitMonitor.update { false }
            }
            Log.d(
                TAG,
                "MegaBytes: ${(((usage.bytes / (1024.0 * 1024.0)) * 100).toInt()) / 100.0}"
            )
        } else {
            limitMonitor.update { false }
        }
    }

    override fun updateStatus(value: NodeStatus) {
        status.update { value }
    }
}
