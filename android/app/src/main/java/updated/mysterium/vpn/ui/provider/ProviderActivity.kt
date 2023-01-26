package updated.mysterium.vpn.ui.provider

import android.os.Bundle
import android.view.View
import network.mysterium.vpn.R
import network.mysterium.vpn.databinding.ActivityProviderBinding
import org.koin.android.ext.android.inject
import updated.mysterium.vpn.App
import updated.mysterium.vpn.notification.AppNotificationManager
import updated.mysterium.vpn.ui.base.BaseActivity

class ProviderActivity : BaseActivity() {

    private companion object {
        const val TAG = "ProviderActivity"
    }

    private lateinit var binding: ActivityProviderBinding
    private val viewModel: ProviderViewModel by inject()
    private val notificationManager: AppNotificationManager by inject()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.init(
            deferredMysteriumCoreService = App.getInstance(this).deferredMysteriumCoreService,
            notificationManager = notificationManager
        )
        configure()
        bindsAction()
    }

    override fun showConnectionHint() {
        binding.connectionHint.visibility = View.VISIBLE
        baseViewModel.hintShown()
    }

    private fun configure() {
        initToolbar(binding.manualConnectToolbar)

        viewModel.providerUpdate.observe(this) {
            binding.providerModeSwitch.isChecked = it.active
        }

        viewModel.providerServiceStatus.observe(this) {
            fun getStatusTxt(a: Boolean): Int {
                if (a) {
                    return R.string.service_active_title;
                }
                return R.string.service_idle_title
            }
            binding.titleSvcState1.setText(getStatusTxt(it.active1))
            binding.titleSvcState2.setText(getStatusTxt(it.active2))
            binding.titleSvcState3.setText(getStatusTxt(it.active3))
        }
    }

    private fun bindsAction() {
        binding.manualConnectToolbar.onConnectClickListener {
            navigateToConnectionIfConnectedOrHome()
        }
        binding.manualConnectToolbar.onLeftButtonClicked {
            finish()
        }
        binding.providerModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleProvider(isChecked)
        }
    }

}
