package network.mysterium.provider.ui.screens.settings

import androidx.annotation.StringRes
import network.mysterium.provider.core.UIEffect
import network.mysterium.provider.core.UIEvent
import network.mysterium.provider.core.UIState
import network.mysterium.provider.ui.navigation.NavigationDestination

sealed class Settings {
    sealed class Event : UIEvent {
        object FetchConfig : Event()
        data class ToggleMobileData(val checked: Boolean) : Event()
        data class ToggleLimit(val checked: Boolean) : Event()
        data class ToggleAllowUseOnBattery(val checked: Boolean) : Event()
        data class UpdateLimit(val value: String) : Event()
        object SaveMobileDataLimit : Event()
        object OnContinue : Event()
    }

    data class State(
        val isMobileDataOn: Boolean,
        val isMobileDataLimitOn: Boolean,
        val isAllowUseOnBatteryOn: Boolean,
        val mobileDataLimit: Int?,
        val mobileDataLimitInvalid: Boolean,
        val isSaveButtonEnabled: Boolean,
        val isStartingNode: Boolean,
        val nodeError: NodeError?
    ) : UIState

    data class NodeError(
        @StringRes val messageResId: Int
    )

    sealed class Effect : UIEffect {
        data class Navigation(val destination: NavigationDestination): Effect()
    }
}