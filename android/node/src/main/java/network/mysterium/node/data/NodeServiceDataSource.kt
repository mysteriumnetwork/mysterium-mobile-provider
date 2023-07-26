package network.mysterium.node.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import mysterium.GetBalanceRequest
import mysterium.GetIdentityRequest
import mysterium.MobileNode
import network.mysterium.node.Storage
import network.mysterium.node.core.NodeService
import network.mysterium.node.model.NodeIdentity
import network.mysterium.node.model.NodeServiceType
import network.mysterium.node.network.NetworkReporter
import network.mysterium.node.network.NetworkType

interface NodeServiceDataSource {

    val identity: StateFlow<NodeIdentity>
    val services: Flow<List<NodeServiceType>>
    val balance: Flow<Double>
    val limitMonitor: StateFlow<Boolean>

    suspend fun fetchIdentity()
    suspend fun fetchBalance()
    suspend fun fetchServices()
    suspend fun updateMobileDataUsage(usedBytesPerMonth: Long)
}

class NodeServiceDataSourceImpl(
    private val mobileNode: MobileNode,
    private val storage: Storage,
    private val networkReporter: NetworkReporter,
) : NodeServiceDataSource {

    private val TAG: String = NodeServiceDataSource::class.java.simpleName

    override val identity: MutableStateFlow<NodeIdentity> = MutableStateFlow(NodeIdentity.empty())
    override val services: MutableStateFlow<List<NodeServiceType>> = MutableStateFlow(emptyList())
    override val balance: MutableStateFlow<Double> = MutableStateFlow(0.0)
    override val limitMonitor: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun fetchIdentity() {
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
        val request = GetBalanceRequest()
        request.identityAddress = identity.value.address
        val response = mobileNode.getUnsettledEarnings(request)
        balance.update {
            response.balance
        }
    }


    override suspend fun fetchServices() {
        val json = mobileNode.allServicesState.decodeToString()
        val response = Json.decodeFromString<List<NodeServiceType>>(json)
        services.update {
            response.toSet().toList()
        }
    }

    override suspend fun updateMobileDataUsage(usedBytesPerMonth: Long) {
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

}
