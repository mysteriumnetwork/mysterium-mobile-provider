package network.mysterium.provider.ui.screens.launch

import android.util.Log
import kotlinx.coroutines.Dispatchers
import network.mysterium.node.Node
import network.mysterium.node.model.NodeIdentity
import network.mysterium.node.network.NetworkReporter
import network.mysterium.provider.R
import network.mysterium.provider.core.CoreViewModel
import network.mysterium.provider.ui.navigation.NavigationDestination

class LaunchViewModel(
    private val node: Node,
    private val networkReporter: NetworkReporter
) : CoreViewModel<Launch.Event, Launch.State, Launch.Effect>() {

    private companion object {
        val TAG: String = LaunchViewModel::class.java.simpleName
    }

    init {
        setEffect { Launch.Effect.RequestVpnPermission }
        observeNetworkState()
    }

    override fun createInitialState(): Launch.State {
        return Launch.State(error = null)
    }

    override fun handleEvent(event: Launch.Event) {
        when (event) {
            Launch.Event.InitializeNode -> {
                initializeNode()
            }

            Launch.Event.DeclinedVpnPermission -> {
                setState { copy(error = Launch.InitError(R.string.missing_vpn_permission)) }
            }

            Launch.Event.ConfirmedInitError -> {
                setEffect { Launch.Effect.CloseApp }
            }
        }
    }

    private fun initializeNode() = launch(dispatcher = Dispatchers.IO) {
        if (!networkReporter.isOnline()) {
            setState { copy(error = Launch.InitError(R.string.unable_init_node_no_network)) }
            return@launch
        }
        try {
            node.start()
            node.identity.collect {
                when (it.status) {
                    NodeIdentity.Status.REGISTERED -> {
                        setEffect { Launch.Effect.Navigation(NavigationDestination.Home) }
                    }

                    NodeIdentity.Status.IN_PROGRESS -> {
                        setEffect { Launch.Effect.Navigation(NavigationDestination.NodeUI(false)) }
                    }

                    NodeIdentity.Status.UNKNOWN,
                    NodeIdentity.Status.UNREGISTERED,
                    NodeIdentity.Status.REGISTRATION_ERROR -> {
                        setEffect { Launch.Effect.Navigation(NavigationDestination.Start) }
                    }
                }
            }

        } catch (error: Throwable) {
            Log.e(TAG, "unable to init node", error)
            setState { copy(error = Launch.InitError(R.string.unable_init_node)) }
        }
    }

    private fun observeNetworkState() = launch {
        networkReporter.currentConnectivity.collect {
            if (currentState.error == null) {
                return@collect
            }
            if (it.isNotEmpty()) {
                setState { copy(error = null) }
                initializeNode()
            }
        }
    }
}
