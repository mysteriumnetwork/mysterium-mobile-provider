package network.mysterium.provider.ui.screens.nodeui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import network.mysterium.provider.R
import network.mysterium.provider.ui.screens.settings.views.SwitchOption
import network.mysterium.provider.ui.theme.Paddings
import androidx.compose.ui.tooling.preview.Preview
import network.mysterium.provider.ui.components.buttons.HomeButton
import network.mysterium.provider.ui.components.buttons.SettingsButton
import network.mysterium.provider.ui.components.content.LogoScreenContent
import network.mysterium.provider.ui.components.webview.ComposeWebView
import network.mysterium.provider.ui.navigation.NavigationDestination
import network.mysterium.provider.ui.navigation.params.NodeUiParam
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun NodeUIScreen(
    params: NodeUiParam? = null,
    viewModel: NodeUIViewModel = koinViewModel { parametersOf(params) },
    onNavigate: (NavigationDestination) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.trackNodeUiOpened()
    }
    NodeUIScreenContent(
        state = state,
        onEvent = {
            viewModel.setEvent(it)
        },
        onNavigate = onNavigate
    )
}

@Composable
private fun NodeUIScreenContent(
    state: NodeUI.State,
    onEvent: (NodeUI.Event) -> Unit,
    onNavigate: (NavigationDestination) -> Unit
) {

    LogoScreenContent(
        navLeading = {
            if (state.isRegistered) {
                HomeButton {
                    onNavigate(NavigationDestination.Home)
                }
            }
        },
        navTrailing = {
            if (state.isRegistered) {
                SettingsButton {
                    onNavigate(NavigationDestination.Settings())
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Native Public toggle above the web dashboard: the dashboard itself is
            // a web page bundled inside the node library, so its Services card can't
            // be extended from the app.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Paddings.default, vertical = Paddings.small)
            ) {
                SwitchOption(
                    title = stringResource(id = R.string.service_public),
                    checked = state.publicServiceEnabled,
                    onCheckedChange = { onEvent(NodeUI.Event.TogglePublicService(it)) },
                    content = {
                        Text(
                            text = stringResource(id = R.string.service_public_description),
                            modifier = Modifier.padding(top = Paddings.small)
                        )
                    }
                )
            }
            ComposeWebView(
                modifier = Modifier.weight(1f),
                url = state.url,
                onReload = {
                    onEvent(NodeUI.Event.SetReloadCallback(it))
                },
                onLoadUrl = {
                    onEvent(NodeUI.Event.UrlLoaded(it))
                },
                ignoreUrls = state.ignoredUrls
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun NodeUIScreenContentPreview() {
    NodeUIScreenContent(
        state = NodeUI.State(
            url = "http://localhost:4449",
            reload = {},
            isRegistered = false,
            ignoredUrls = emptyList(),
            publicServiceEnabled = true,
        ),
        onEvent = {},
        onNavigate = {}
    )
}
