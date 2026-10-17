package network.mysterium.node.model

/**
 * High-level node state derived from identity registration, connectivity
 * and the provider services, used for the in-app status line and the
 * notification status text.
 */
enum class NodeStatus {
    /** Node process not running yet. */
    OFFLINE,

    /** Starting up, waiting for the node to settle. */
    CONNECTING,

    /** Registered and providing. */
    ONLINE,

    /** No network connection — provider is paused until it comes back. */
    NO_NETWORK,

    /** Online but the provider is not serving (on battery, mobile-data
     *  limit reached, etc.). */
    PAUSED,

    /** Node online but identity is not registered — open the app to fix. */
    UNREGISTERED,

    /** Something went wrong while starting or monitoring the provider. */
    FAILED,

    /** Not known yet. */
    UNKNOWN
}
