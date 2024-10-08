package network.mysterium.provider.di

import network.mysterium.provider.ui.navigation.params.NodeUiParam
import network.mysterium.provider.ui.screens.home.HomeViewModel
import network.mysterium.provider.ui.screens.launch.LaunchViewModel
import network.mysterium.provider.ui.screens.nodeui.NodeUIViewModel
import network.mysterium.provider.ui.screens.settings.SettingsViewModel
import network.mysterium.provider.ui.screens.start.StartScreenViewModel
import network.mysterium.provider.ui.screens.tac.TACViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModels = module {
    viewModel {
        LaunchViewModel(
            node = get(),
            networkReporter = get()
        )
    }
    viewModel {
        TACViewModel(node = get())
    }
    viewModel {
        SettingsViewModel(node = get(), analytics = get())
    }
    viewModel { (params: NodeUiParam?) ->
        NodeUIViewModel(
            params = params,
            node = get(),
            context = get(),
            deeplinkRedirectionInteractor = get(),
            analytics = get(),
        )
    }
    viewModel {
        HomeViewModel(node = get())
    }

    viewModel {
        StartScreenViewModel(analytics = get())
    }
}
