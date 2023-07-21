package network.mysterium.provider

object Config {
    const val minMobileDataLimit = 50
    const val maxMobileDataLimit = 999999
    const val payPalRedirectUrl = "https://pilvytis.mysterium.network/api/v2/payment/paypal/redirect"
    const val stripeRedirectUrl = "https://pilvytis.mysterium.network/api/v2/payment/stripe/redirect"
    const val mystNodesUrl = "mystnodes.com"
    const val redirectUriReplacement = "tequilapi/auth"
    const val helpLink = "https://help.mystnodes.com"

    const val authorizationGrantPath = "/#/auth-sso?authorizationGrant="
    const val deeplinkScheme = "mystnodes://sso"
}
