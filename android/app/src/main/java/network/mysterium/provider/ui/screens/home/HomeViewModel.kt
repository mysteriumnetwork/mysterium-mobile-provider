package network.mysterium.provider.ui.screens.home

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import network.mysterium.node.Node
import network.mysterium.node.model.NodeServiceType
import network.mysterium.provider.core.CoreViewModel

class HomeViewModel(
    private val node: Node
) : CoreViewModel<Home.Event, Home.State, Home.Effect>() {

    private val ignoreServices: List<NodeServiceType.Service> =
        listOf(NodeServiceType.Service.WIREGUARD)

    init {
        viewModelScope.launch(defaultErrorHandler) { node.start() }
        setEvent(Home.Event.Load)
    }

    override fun createInitialState(): Home.State {
        return Home.State(
            services = emptyList(),
            isLimitReached = false,
            balance = 0.0
        )
    }

    override fun handleEvent(event: Home.Event) {
        when (event) {
            Home.Event.Load -> {
                observeServices()
                observeBalance()
                observeLimit()
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
}
