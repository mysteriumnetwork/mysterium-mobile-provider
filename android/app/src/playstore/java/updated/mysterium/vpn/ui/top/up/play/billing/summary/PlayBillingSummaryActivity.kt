package updated.mysterium.vpn.ui.top.up.play.billing.summary

import android.util.Log
import network.mysterium.vpn.databinding.PopUpCardPaymentBinding
import org.koin.android.ext.android.inject
import updated.mysterium.vpn.common.extensions.TAG
import updated.mysterium.vpn.exceptions.BaseNetworkException
import updated.mysterium.vpn.model.payment.OrderRequestInfo
import updated.mysterium.vpn.model.payment.PaymentStatus
import updated.mysterium.vpn.model.payment.PlayBillingOrderRequestInfo
import updated.mysterium.vpn.model.pushy.PushyTopic
import updated.mysterium.vpn.model.top.up.TopUpPlayBillingCardItem
import updated.mysterium.vpn.ui.top.up.select.country.SelectCountryActivity
import updated.mysterium.vpn.ui.top.up.summary.SummaryActivity

class PlayBillingSummaryActivity : SummaryActivity() {

    companion object {
        const val SKU_EXTRA_KEY = "SKU_EXTRA_KEY"
    }

    private val viewModel: PlayBillingSummaryViewModel by inject()
    private var topUpPlayBillingCardItem: TopUpPlayBillingCardItem? = null

    override fun subscribeViewModel() {
        viewModel.paymentSuccessfully.observe(this) { paymentStatus ->
            if (paymentStatus == PaymentStatus.STATUS_PAID) {
                paymentConfirmed()
            }
        }
        viewModel.playBillingDataSource.purchaseUpdatedFlow.observe(this) {
            setButtonAvailability(false)
            showPaymentPopUp()
        }
    }

    override fun getOrderRequestInfo(): PlayBillingOrderRequestInfo? {
        this.topUpPlayBillingCardItem = intent.extras?.getParcelable(SKU_EXTRA_KEY)
        val country =
            intent.extras?.getString(SelectCountryActivity.COUNTRY_EXTRA_KEY) ?: return null
        val state = intent.extras?.getString(SelectCountryActivity.STATE_EXTRA_KEY) ?: ""
        return topUpPlayBillingCardItem?.amountUsd?.let {
            PlayBillingOrderRequestInfo(it, country, state)
        }
    }

    override fun getOrder(info: OrderRequestInfo?) {
        (info as? PlayBillingOrderRequestInfo?)?.let {
            viewModel.getPlayBillingPayment(it).observe(this) { result ->
                result.onSuccess { order ->
                    topUpPlayBillingCardItem = topUpPlayBillingCardItem?.copy(id = order.id)
                    if (topUpPlayBillingCardItem?.id?.isEmpty() == true) {
                        showNoAmountPopUp { getOrder(it) }
                        return@onSuccess
                    }
                    onSuccess.invoke(order)
                }
                result.onFailure { error ->
                    if (error is BaseNetworkException) {
                        Log.e(TAG, error.getMessage(this))
                        onFailure.invoke(error)
                    } else {
                        Log.e(TAG, error.localizedMessage ?: error.toString())
                        wifiNetworkErrorPopUp {
                            getOrder(info)
                        }
                    }
                }
            }
        }
    }

    override fun launchPayment() {
        if (topUpPlayBillingCardItem?.id?.isEmpty() == true) return

        topUpPlayBillingCardItem?.let {
            viewModel.playBillingDataSource.launchBillingFlow(
                this@PlayBillingSummaryActivity,
                it.sku,
                it.id
            )
        }
    }

    private fun paymentConfirmed() {
        setButtonAvailability(true)
        pushyNotifications?.unsubscribe(PushyTopic.PAYMENT_FALSE)
        pushyNotifications?.subscribe(PushyTopic.PAYMENT_TRUE)
        pushyNotifications?.subscribe("USD")
        viewModel.clearPopUpTopUpHistory()
        registerAccount()
    }

    private fun showPaymentPopUp() {
        val bindingPopUp = PopUpCardPaymentBinding.inflate(layoutInflater)
        val dialog = createPopUp(bindingPopUp.root, false)
        bindingPopUp.okayButton.setOnClickListener {
            dialog.dismiss()
            navigateToHome(true)
        }
        dialog.show()
    }

}
