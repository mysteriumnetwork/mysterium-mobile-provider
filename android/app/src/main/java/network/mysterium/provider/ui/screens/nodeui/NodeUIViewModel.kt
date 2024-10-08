package network.mysterium.provider.ui.screens.nodeui

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import network.mysterium.node.Node
import network.mysterium.node.analytics.NodeAnalytics
import network.mysterium.node.analytics.event.AnalyticsEvent
import network.mysterium.node.model.NodeIdentity
import network.mysterium.provider.Config
import network.mysterium.provider.DeeplinkPath
import network.mysterium.provider.core.CoreViewModel
import network.mysterium.provider.domain.DeeplinkRedirectionInteractor
import network.mysterium.provider.ui.navigation.params.NodeUiParam

class NodeUIViewModel(
    private val params: NodeUiParam?,
    private val node: Node,
    private val context: Context,
    private val deeplinkRedirectionInteractor: DeeplinkRedirectionInteractor,
    private val analytics: NodeAnalytics,
) : CoreViewModel<NodeUI.Event, NodeUI.State, NodeUI.Effect>() {

    init {
        setEvent(NodeUI.Event.Load)
        observeIdentity()
    }

    override fun createInitialState(): NodeUI.State {
        return NodeUI.State(
            url = "",
            reload = {},
            isRegistered = false,
            ignoredUrls = listOf(
                Config.stripeRedirectUrl,
                Config.payPalRedirectUrl,
            )
        )
    }

    override fun handleEvent(event: NodeUI.Event) {
        when (event) {
            NodeUI.Event.Load -> {
                setState {
                    copy(
                        url = when (params?.scheme) {
                            DeeplinkPath.Scheme.CLAIM -> node.nodeUIUrl + "/#/node-claim" + Config.deeplinkClaim.queryPath + params.parameter
                            DeeplinkPath.Scheme.SSO -> node.nodeUIUrl + "/#/auth-sso" + Config.deeplinkSSO.queryPath + params.parameter
                            DeeplinkPath.Scheme.CLICKBOARDING -> node.nodeUIUrl + "/#/" + Config.deepLinkOnboardingClicking.scheme.scheme + Config.deepLinkOnboardingClicking.queryPath + params.parameter
                            null -> node.nodeUIUrl
                        }
                    )
                }
            }

            is NodeUI.Event.UrlLoaded -> {
                handleUrl(event.url)
            }

            is NodeUI.Event.SetReloadCallback -> {
                setState { copy(reload = event.reload) }
            }
        }
    }

    private fun redirectionUrlReplacement(url: String) {
        launch {
            val result = withContext(Dispatchers.IO) {
                deeplinkRedirectionInteractor.getDeeplinkRedirection(url)
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(result.link)
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                })
        }
    }

    private fun observeIdentity() = launch {
        node.identity.collectLatest {
            setState { copy(isRegistered = it.status == NodeIdentity.Status.REGISTERED) }
        }
    }

    private fun handleUrl(url: Uri) {
        val path = url.toString()
        Log.d("NodeUI", "Url loaded: $path")
        val stringUrl = url.toString()
        when {
            stringUrl.contains(Config.redirectUriReplacement) -> redirectionUrlReplacement(stringUrl)
            path.startsWith(Config.stripeRedirectUrl) || path.startsWith(Config.payPalRedirectUrl) -> {
                launch {
                    delay(3000)
                    currentState.reload()
                }
            }

            else -> {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = url
                        addFlags(FLAG_ACTIVITY_NEW_TASK)
                    })
            }
        }
    }

    fun trackNodeUiOpened() {
        analytics.trackEvent(AnalyticsEvent.NodeUiOpenedEvent)
    }

}
