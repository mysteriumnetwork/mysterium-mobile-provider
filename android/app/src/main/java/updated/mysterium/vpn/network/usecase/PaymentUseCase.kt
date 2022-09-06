package updated.mysterium.vpn.network.usecase

import com.google.gson.Gson
import mysterium.CreatePaymentGatewayOrderReq
import mysterium.OrderUpdatedCallbackPayload
import updated.mysterium.vpn.core.NodeRepository
import updated.mysterium.vpn.model.payment.*

class PaymentUseCase(private val nodeRepository: NodeRepository) {

    companion object {
        private const val currency = "USD"
    }

    suspend fun createPlayBillingPaymentGatewayOrder(
        identityAddress: String,
        amountUsd: Double,
        country: String,
        state: String
    ): Order {
        val req = CreatePaymentGatewayOrderReq().apply {
            this.payCurrency = currency
            this.country = country
            this.state = state
            this.identityAddress = identityAddress
            this.amountUSD = amountUsd.toString()
            this.gateway = Gateway.GOOGLE.gateway
        }
        return nodeRepository.createPlayBillingPaymentGatewayOrder(req)
    }

    suspend fun createCoingatePaymentGatewayOrder(
        currency: String,
        country: String,
        state: String,
        identityAddress: String,
        amountUSD: Double,
        isLightning: Boolean
    ): Order {
        val req = CreatePaymentGatewayOrderReq().apply {
            this.payCurrency = currency
            this.country = country
            this.state = state
            this.identityAddress = identityAddress
            this.amountUSD = amountUSD.toString()
            this.gateway = Gateway.COINGATE.gateway
            this.gatewayCallerData = Gson()
                .toJson(Lightning(isLightning))
                .toString()
                .toByteArray()
        }
        return nodeRepository.createCoingatePaymentGatewayOrder(req)
    }

    suspend fun createCardPaymentGatewayOrder(
        country: String,
        state: String,
        identityAddress: String,
        amountUSD: Double,
        currency: String,
        gateway: String
    ): Order {
        val req = CreatePaymentGatewayOrderReq().apply {
            this.country = country
            this.state = state
            this.payCurrency = currency
            this.identityAddress = identityAddress
            this.amountUSD = amountUSD.toString()
            this.gateway = gateway
            this.gatewayCallerData = Gson()
                .toJson(CardGatewayLocalisation("US"))
                .toString()
                .toByteArray()
        }
        return nodeRepository.createCardPaymentGatewayOrder(req)
    }

    suspend fun paymentOrderCallback(
        action: (OrderUpdatedCallbackPayload) -> Unit
    ) = nodeRepository.registerOrderUpdatedCallback {
        action.invoke(it)
    }

    suspend fun getGateways() = nodeRepository.getGateways()

    suspend fun gatewayClientCallback(purchase: Purchase) =
        nodeRepository.gatewayClientCallback(purchase)

}
