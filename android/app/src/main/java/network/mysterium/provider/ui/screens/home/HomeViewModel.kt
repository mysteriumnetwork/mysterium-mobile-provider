package network.mysterium.provider.ui.screens.home

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import network.mysterium.node.Node
import network.mysterium.node.model.NodeServiceType
import network.mysterium.node.model.NodeStatus
import network.mysterium.provider.core.CoreViewModel

class HomeViewModel(
    private val node: Node
) : CoreViewModel<Home.Event, Home.State, Home.Effect>() {

    private val ignoreServices: List<NodeServiceType.Service> =
        emptyList()

    init {
        viewModelScope.launch(defaultErrorHandler) { node.start() }
        setEvent(Home.Event.Load)
    }

    override fun createInitialState(): Home.State {
        // NOTE: runs from CoreViewModel's init, i.e. during the super constructor —
        // subclass constructor parameters (node) are NOT yet assigned here.
        // Keep this free of `node` access; observeStatus() fills nodeStatus
        // on the first emission anyway.
        return Home.State(
            services = emptyList(),
            isLimitReached = false,
            balance = 0.0,
            nodeStatus = NodeStatus.UNKNOWN
        )
    }

    override fun handleEvent(event: Home.Event) {
        when (event) {
            Home.Event.Load -> {
                observeServices()
                observeBalance()
                observeLimit()
                observeStatus()
            }
        }
    }

    private fun observeServices() = launch {
        node.services.collect {
            setState {
                copy(services = it.filterNot { it.id in ignoreServices }.sortedBy { it.id.order })
            }
        }
    }

    private fun observeBalance() = launch {
        node.balance.collect {
            setState { copy(balance = it) }
        }
    }

    private fun observeLimit() = launch {
        node.limitMonitor.collect {
            setState { copy(isLimitReached = it) }
        }
    }

    private fun observeStatus() = launch {
        node.status.collect {
            setState { copy(nodeStatus = it) }
        }
    }
}
