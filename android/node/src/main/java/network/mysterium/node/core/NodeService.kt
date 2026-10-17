package network.mysterium.node.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mysterium.MobileNode
import network.mysterium.node.Storage
import network.mysterium.node.analytics.NodeAnalytics
import network.mysterium.node.analytics.event.AnalyticsEvent
import network.mysterium.node.battery.BatteryStatus
import network.mysterium.node.data.NodeServiceDataSource
import network.mysterium.node.extensions.isFirstDayOfMonth
import network.mysterium.node.extensions.nextDay
import network.mysterium.node.model.NodeIdentity
import network.mysterium.node.model.NodeServiceType
import network.mysterium.node.model.NodeStatus
import network.mysterium.node.model.NodeUsage
import network.mysterium.node.monitoring.MonitoringDiagnostics
import network.mysterium.node.monitoring.MonitoringReport
import network.mysterium.node.monitoring.MonitoringState
import network.mysterium.node.network.NetworkReporter
import network.mysterium.node.network.NetworkType
import network.mysterium.node.utils.cancelCatching
import network.mystrium.node.R
import org.koin.android.ext.android.inject
import java.util.Calendar
import java.util.Date
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.timer

class NodeService : Service() {

    private companion object {
        const val CHANNEL_ID = "mystnodes.channel"
        const val NOTIFICATION_ID = 1

        /**
         * Node user-config key that persists which provider services
         * startProvider() should bring up (comma separated service types).
         */
        const val ACTIVE_SERVICES_KEY = "active-services"
        const val ALL_SERVICES_VALUE = "wireguard,scraping,data_transfer,dvpn,monitoring"
        const val SERVICES_WITHOUT_PUBLIC_VALUE = "scraping,data_transfer,dvpn,monitoring"
        const val PUBLIC_SERVICE_ID = "wireguard"

        // Monitoring troubleshooting.
        // Auto-switch the Public (wireguard) service off when the monitoring agent
        // can reach every other service but never reaches this one.
        const val AUTO_DISABLE_FAILING_PUBLIC = true
        const val DIAGNOSTICS_PREFS = "mysterium.node.diagnostics"
        val MONITORING_FIRST_CHECK_DELAY = TimeUnit.MINUTES.toMillis(2)
        val MONITORING_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(15)
        // Mysterium refreshes the verdict about hourly, so act only after several
        // consecutive failed checks.
        const val MONITORING_FAILURES_BEFORE_ACTION = 3
        // Repeated restarts get rate-limited by the quality oracle - bounce rarely.
        val MONITORING_RECOVERY_COOLDOWN = TimeUnit.HOURS.toMillis(6)

        val BALANCE_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1)
        // The notification uptime is a system-ticked chronometer, so the status
        // only needs a slow safety refresh (state changes also push updates).
        val STATUS_REFRESH_INTERVAL = TimeUnit.SECONDS.toMillis(30)
        // Connectivity flaps (wifi <-> mobile) settle for this long before the
        // provider is re-evaluated.
        const val NETWORK_SETTLE_DELAY = 2_000L
        // How long to wait before auto-starting the provider again after a
        // failed attempt, so a broken node cannot restart-loop.
        val AUTO_START_RETRY_COOLDOWN = TimeUnit.SECONDS.toMillis(30)
        // Breathing room between stopProvider() and startProvider() so the
        // node's services manager can finish tearing sessions down before the
        // restart races ahead of it.
        val RESTART_SETTLE_DELAY = 3_000L
        val TAG: String = NodeService::class.java.simpleName
    }

    private var isStarted: Boolean = false

    private val nodeContainer by inject<NodeContainer>()
    private var mobileNode: MobileNode? = null
    private val nodeServiceDataSource by inject<NodeServiceDataSource>()
    private val networkReporter by inject<NetworkReporter>()
    private val storage by inject<Storage>()
    private val batteryStatus by inject<BatteryStatus>()
    private val analytics by inject<NodeAnalytics>()

    private var dispatcher = Dispatchers.IO
    private val defaultErrorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, throwable.message, throwable)
    }

    private val isNotificationShown: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher + defaultErrorHandler)

    // Serializes every provider start/stop so concurrent triggers (network,
    // battery, limit, identity, restart button) cannot race each other.
    private val servicesMutex = Mutex()

    private val monitoringDiagnostics = MonitoringDiagnostics()

    @Volatile
    private var monitoringReport: MonitoringReport? = null
    private var consecutiveMonitoringFailures = 0
    private var lastMonitoringRecoveryAt = 0L

    private val diagnosticsPrefs by lazy {
        getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
    }

    /** Whether the Public (wireguard) service is part of the active services. */
    private var isPublicServiceEnabled: Boolean
        get() = storage.config.publicServiceEnabled
        set(value) {
            storage.config = storage.config.copy(publicServiceEnabled = value)
        }

    private var balanceTimer: Timer? = null
    private var endOfDayTimer: Timer? = null
    private var statusTimer: Timer? = null
    private var mobileLimitJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var serviceStartedAt: Long = 0
    private var lastAutoStartAttemptAt: Long = 0
    private var contentIntent: PendingIntent? = null

    private val notificationManager: NotificationManager
        by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        // The service can be recreated by the system (START_STICKY) without the app UI
        // bound to it — start in foreground immediately and wire everything ourselves.
        serviceStartedAt = System.currentTimeMillis()
        startForegroundNotification()
        acquireWakeLock()
        startInternal()
        storage.shouldRun = true
        observeNetworkUsage()
        observeNetworkStatus()
        startEndOfDayTimer()
        observeBatteryStatus()
        observeLimitStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started (not only bound) so the service survives task removal; STICKY so the
        // system recreates it after a low-memory kill. intent is null after a sticky
        // restart — never read extras from it.
        if (!isStarted) {
            startInternal()
        }
        return START_STICKY
    }

    private fun startInternal() {
        if (isStarted) return
        isStarted = true
        storage.shouldRun = true
        scope.launch {
            mobileNode = nodeContainer.getInstance()
        }
        scope.launch {
            nodeServiceDataSource.services.collectLatest {
                val anyActive = it.any { service ->
                    service.state == NodeServiceType.State.STARTING ||
                            service.state == NodeServiceType.State.RUNNING
                }
                if (anyActive) {
                    // Something is (still) up — re-check the stop conditions only,
                    // never start from this branch.
                    updateNodeServices(isSkipStart = true)
                } else if (it.isNotEmpty() && shouldAutoStartProvider()) {
                    // Everything is stopped but the user wants the node running —
                    // turn the services back on automatically.
                    lastAutoStartAttemptAt = System.currentTimeMillis()
                    updateNodeServices()
                }
                updateNodeStatus()
            }
        }
        registerListeners()
        startBalanceTimer()
        startStatusTimer()
        startNode()
        observeNotification()
        observeStatusForNotification()
        observeMonitoring()
    }

    /**
     * True when the provider should be (re)started automatically: the user
     * enabled the node at least once, the identity is registered and the
     * previous attempt is old enough to not restart-loop.
     */
    private fun shouldAutoStartProvider(): Boolean {
        if (!storage.shouldRun) return false
        if (nodeServiceDataSource.identity.value.status != NodeIdentity.Status.REGISTERED) return false
        return System.currentTimeMillis() - lastAutoStartAttemptAt > AUTO_START_RETRY_COOLDOWN
    }

    override fun onBind(p0: Intent?): IBinder {
        return Bridge()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "mystnodes:node-service"
        ).apply { acquire() }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        // The notification must be attached before any slow node work — never
        // gate the first startForeground() behind network operations (or a
        // missing launch intent): that would crash after the 5s FGS deadline.
        val notification = buildNotification(
            title = getString(R.string.notification_status_connecting)
        )
        startForegroundWithConnectedDeviceType(notification.build())
        isNotificationShown.value = true
    }

    private fun buildNotification(title: String, text: String? = null): NotificationCompat.Builder {
        if (contentIntent == null) {
            // Use our own package name (not a hardcoded id) and tolerate a
            // missing launch intent — the notification still has to be built.
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                contentIntent = PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setColor(Color.WHITE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(LongArray(0))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            // Live uptime: the system ticks the chronometer itself, so the app
            // does not have to re-post the notification every second.
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(serviceStartedAt)

        if (text != null) {
            builder.setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        contentIntent?.let { builder.setContentIntent(it) }
        return builder
    }

    private fun startForegroundWithConnectedDeviceType(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        isNotificationShown.value = false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    /**
     * Runs a node call and logs (instead of propagating) any failure, so one
     * failing call never prevents the ones after it.
     */
    private suspend fun safely(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.e(TAG, "$label failed", error)
        }
    }

    private fun startNode() = scope.launch {
        safely("fetchServices") { nodeServiceDataSource.fetchServices() }
        safely("fetchIdentity") { nodeServiceDataSource.fetchIdentity() }
        safely("fetchBalance") { nodeServiceDataSource.fetchBalance() }
    }

    private fun registerListeners() {
        scope.launch {
            mobileNode = nodeContainer.getInstance()

            mobileNode?.registerServiceStatusChangeCallback { _, _ ->
                scope.launch {
                    safely("fetchServices") { nodeServiceDataSource.fetchServices() }
                }
            }

            mobileNode?.registerIdentityRegistrationChangeCallback { _, _ ->
                scope.launch {
                    safely("fetchIdentity") { nodeServiceDataSource.fetchIdentity() }
                }
            }
        }
    }

    private fun observeNotification() {
        combine(
            isNotificationShown,
            nodeServiceDataSource.identity
        ) { isNotificationShown, identity ->
            if (identity.status == NodeIdentity.Status.REGISTERED) {
                if (!isNotificationShown) {
                    startForegroundNotification()
                }
                // Registered — make sure the provider is serving.
                updateNodeServices()
            } else {
                updateNodeStatus()
                if (isNotificationShown) {
                    updateNotification()
                }
            }
        }
            .launchIn(scope)
    }

    /**
     * Re-posts the notification only when the node status actually changes
     * (StateFlow already drops equal values).
     */
    private fun observeStatusForNotification() {
        nodeServiceDataSource.status
            .onEach { updateNotification() }
            .launchIn(scope)
    }

    /**
     * Derives the high-level node status from identity, connectivity,
     * service states and pause conditions.
     */
    private fun currentNodeStatus(): NodeStatus {
        val identityStatus = nodeServiceDataSource.identity.value.status
        val services = nodeServiceDataSource.services.value
        return when {
            !networkReporter.isOnline() -> NodeStatus.NO_NETWORK

            identityStatus == NodeIdentity.Status.REGISTRATION_ERROR -> NodeStatus.FAILED

            identityStatus == NodeIdentity.Status.UNREGISTERED -> NodeStatus.UNREGISTERED

            identityStatus == NodeIdentity.Status.IN_PROGRESS ||
                    identityStatus == NodeIdentity.Status.UNKNOWN -> NodeStatus.CONNECTING

            // Registered from here on — reflect what the services do.
            services.any { it.state == NodeServiceType.State.RUNNING } -> NodeStatus.ONLINE

            services.any { it.state == NodeServiceType.State.STARTING } -> NodeStatus.CONNECTING

            nodeServiceDataSource.limitMonitor.value -> NodeStatus.PAUSED

            !storage.config.allowUseOnBattery && !batteryStatus.isCharging.value ->
                NodeStatus.PAUSED

            else -> NodeStatus.CONNECTING
        }
    }

    private fun updateNodeStatus() {
        nodeServiceDataSource.updateStatus(currentNodeStatus())
    }

    /**
     * Refreshes the foreground notification with the current status and, when
     * Mysterium's monitoring has something to say, the reason + what to try.
     * The uptime itself is a system-ticked chronometer. No-op while the
     * foreground notification is intentionally removed.
     */
    private fun updateNotification() {
        if (!isNotificationShown.value) return
        val status = nodeServiceDataSource.status.value
        val title = when (status) {
            NodeStatus.ONLINE -> getString(R.string.notification_status_online)
            NodeStatus.CONNECTING -> getString(R.string.notification_status_connecting)
            NodeStatus.NO_NETWORK -> getString(R.string.notification_status_no_network)
            NodeStatus.PAUSED -> getString(R.string.notification_status_paused)
            NodeStatus.UNREGISTERED -> getString(R.string.notification_status_unregistered)
            NodeStatus.FAILED -> getString(R.string.notification_status_failed)
            NodeStatus.OFFLINE -> getString(R.string.notification_status_offline)
            NodeStatus.UNKNOWN -> getString(R.string.notification_status_connecting)
        }
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(title = title, text = monitoringNotificationText()).build()
        )
    }

    private fun monitoringNotificationText(): String? {
        val reportText = monitoringReport?.notificationText() ?: return null
        return if (isPublicServiceEnabled) {
            reportText
        } else {
            "Public (wireguard) is off - it was switched off automatically after " +
                    "failing monitoring.\n$reportText"
        }
    }

    private fun startBalanceTimer() {
        balanceTimer?.cancel()
        balanceTimer = fixedRateTimer(
            initialDelay = BALANCE_CHECK_INTERVAL,
            period = BALANCE_CHECK_INTERVAL
        ) {
            scope.launch {
                // Report aliveness for the watchdog worker first, so a failing
                // node call below cannot make a healthy service look dead.
                storage.lastHeartbeat = System.currentTimeMillis()
                // Refresh the service states too so the auto-start collector
                // re-evaluates once a minute even if nothing else changed.
                safely("fetchServices") { nodeServiceDataSource.fetchServices() }
                safely("fetchBalance") { nodeServiceDataSource.fetchBalance() }
            }
        }
    }

    private fun startStatusTimer() {
        statusTimer?.cancel()
        statusTimer = fixedRateTimer(
            initialDelay = STATUS_REFRESH_INTERVAL,
            period = STATUS_REFRESH_INTERVAL
        ) {
            // Safety refresh; the notification follows the status flow.
            updateNodeStatus()
        }
    }

    private fun cancelTimers() {
        statusTimer?.cancel()
        statusTimer = null
        balanceTimer?.cancel()
        balanceTimer = null
        endOfDayTimer?.cancel()
        endOfDayTimer = null
    }

    private fun observeNetworkUsage() {
        mobileLimitJob?.cancelCatching()
        mobileLimitJob = scope.launch {
            networkReporter.monitorUsage(NetworkType.MOBILE)
                .collectLatest { nodeServiceDataSource.updateMobileDataUsage(it) }
        }
    }

    private fun observeNetworkStatus() = scope.launch {
        // collectLatest + delay = debounce: a burst of connectivity changes
        // only triggers one re-evaluation once the network has settled.
        networkReporter.currentConnectivity.collectLatest {
            delay(NETWORK_SETTLE_DELAY)
            updateNodeServices()
        }
    }

    private fun startEndOfDayTimer() {
        endOfDayTimer?.cancel()
        endOfDayTimer = timer(
            startAt = Calendar.getInstance().nextDay(),
            period = TimeUnit.DAYS.toMillis(1)
        ) {
            resetMobileDataUsageIfNeeded()
        }
    }

    private fun observeBatteryStatus() = scope.launch {
        batteryStatus.isCharging.collect {
            updateNodeServices()
        }
    }

    private fun observeLimitStatus() = scope.launch {
        nodeServiceDataSource.limitMonitor.collect {
            updateNodeServices()
        }
    }

    private fun resetMobileDataUsageIfNeeded() {
        val calendar = Calendar.getInstance()
        val startDate = Date(storage.usage.startTime)
        val today = Date()
        if (calendar.isFirstDayOfMonth && today.after(startDate)) {
            storage.usage = NodeUsage(Date().time, 0)
        }
    }

    // ---- "Monitoring failed" troubleshooting -------------------------------------------

    /**
     * Periodically asks Mysterium (and the local node) how monitoring sees this
     * node, logs the reason + hints (adb logcat -s NodeService MonitoringDiagnostics)
     * and shows them in the notification.
     */
    private fun observeMonitoring() = scope.launch {
        // Give the node time to register and bring its services up first.
        delay(MONITORING_FIRST_CHECK_DELAY)
        while (true) {
            safely("monitoring check") { runMonitoringCheck() }
            delay(MONITORING_CHECK_INTERVAL)
        }
    }

    private suspend fun runMonitoringCheck() {
        val identity = nodeServiceDataSource.identity.value
        if (identity.status != NodeIdentity.Status.REGISTERED) return

        val report = monitoringDiagnostics.check(identity.address, isPublicServiceEnabled)
        monitoringReport = report
        Log.i(TAG, "monitoring check: ${report.state} - ${report.summary}")
        report.hints.forEach { Log.i(TAG, "monitoring hint: $it") }

        if (report.state == MonitoringState.FAILED) {
            consecutiveMonitoringFailures++
            actOnMonitoringFailure(report)
        } else {
            consecutiveMonitoringFailures = 0
        }
        updateNotification()
    }

    /**
     * Conservative self-healing after several failed checks in a row:
     *  1. If the Public (wireguard) service is the only one monitoring cannot
     *     reach, switch just that service off and restart the provider once.
     *  2. Otherwise a gentle provider restart, at most once per cooldown (restart
     *     loops make things worse: the quality oracle rate-limits them).
     */
    private suspend fun actOnMonitoringFailure(report: MonitoringReport) {
        if (consecutiveMonitoringFailures < MONITORING_FAILURES_BEFORE_ACTION) return
        if (!storage.shouldRun) return
        val status = nodeServiceDataSource.status.value
        if (status == NodeStatus.NO_NETWORK || status == NodeStatus.PAUSED) return

        val publicIsCulprit = PUBLIC_SERVICE_ID in report.failingServices &&
                report.passingServices.isNotEmpty()
        if (AUTO_DISABLE_FAILING_PUBLIC && isPublicServiceEnabled && publicIsCulprit) {
            Log.w(TAG, "Public (wireguard) keeps failing monitoring while other services pass - switching it off")
            isPublicServiceEnabled = false
            consecutiveMonitoringFailures = 0
            lastMonitoringRecoveryAt = System.currentTimeMillis()
            bounceProvider()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastMonitoringRecoveryAt < MONITORING_RECOVERY_COOLDOWN) return
        lastMonitoringRecoveryAt = now
        consecutiveMonitoringFailures = 0
        Log.w(TAG, "Monitoring failed repeatedly - soft restarting the provider")
        bounceProvider()
    }

    /** Stops the provider, lets it settle and starts it again with the full service set. */
    private suspend fun bounceProvider() = withContext(dispatcher) {
        val node = mobileNode ?: nodeContainer.getInstance().also { mobileNode = it }
        // Hold the services lock for the whole bounce so no other trigger
        // can start the provider in the middle of the restart.
        servicesMutex.withLock {
            // Cut the provider sessions…
            node.stopProvider()
            // …give the services manager a moment to tear everything down, so
            // the start that follows cannot race against the stop…
            delay(RESTART_SETTLE_DELAY)
            // …and bring the provider back up with the full service set.
            ensureAllServicesActive(node)
            node.startProvider()
        }
    }

    private suspend fun updateNodeServices(isSkipStart: Boolean = false) = withContext(dispatcher) {
        servicesMutex.withLock {
            val config = storage.config
            // The mobile node may not be initialized yet when this runs for the
            // first time — fetching it here (cached afterwards) instead of relying
            // on the field being set avoids losing the very first provider start.
            val node = mobileNode ?: nodeContainer.getInstance().also { mobileNode = it }
            val wifiOption = networkReporter.isConnected(NetworkType.WIFI)
            val mobileDataOption =
                config.useMobileData && networkReporter.isConnected(NetworkType.MOBILE)
            val batteryOption = if (config.allowUseOnBattery) true else batteryStatus.isCharging.value
            if (batteryOption && (wifiOption || (mobileDataOption && !isMobileLimitReached()))) {
                if (!isSkipStart) {
                    ensureAllServicesActive(node)
                    node.startProvider()
                    analytics.trackEvent(AnalyticsEvent.ToggleAnalyticsEvent.NodeUiState(isEnabled = true))
                }
            } else {
                node.stopProvider()
                analytics.trackEvent(AnalyticsEvent.ToggleAnalyticsEvent.NodeUiState(isEnabled = false))
            }
            updateNodeStatus()
        }
    }

    /**
     * The node persists an "active-services" list in its user config. On the
     * very first run (before terms were agreed) the mobile node writes
     * "scraping" only, so startProvider() would keep starting just scraping
     * forever — data_transfer and dvpn would never toggle on. Ensure the full
     * set (including the public wireguard service, unless it was switched off
     * after failing monitoring) is active before starting; startProvider()
     * itself skips whatever is already running, so this is safe to call
     * repeatedly.
     */
    private fun ensureAllServicesActive(node: MobileNode) {
        try {
            val services =
                if (isPublicServiceEnabled) ALL_SERVICES_VALUE else SERVICES_WITHOUT_PUBLIC_VALUE
            node.setUserConfig(ACTIVE_SERVICES_KEY, services)
        } catch (error: Throwable) {
            // Never block the provider start on a config write — worst case
            // we retry on the next tick.
            Log.e(TAG, "unable to set active-services config", error)
        }
    }

    private fun isMobileLimitReached(): Boolean {
        val config = storage.config
        val usage = storage.usage
        val shouldCheckLimit = config.useMobileData &&
                config.useMobileDataLimit &&
                networkReporter.isConnected(NetworkType.MOBILE)
        val limit = config.mobileDataLimit ?: return false
        return if (shouldCheckLimit) {
            usage.bytes > limit
        } else {
            false
        }
    }

    override fun onDestroy() {
        // Cancel every collector/launch owned by this instance. Without this,
        // each sticky restart / watchdog recreation would leave a zombie set of
        // collectors behind that keeps calling startProvider()/stopProvider().
        scope.cancel()
        cancelTimers()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopSelfService() {
        storage.shouldRun = false
        cancelTimers()
        this.stopSelf()
    }


    internal inner class Bridge : Binder(), NodeServiceBinder {

        override fun start() {
            // Kept for backwards compatibility — the service now wires itself
            // in onCreate/onStartCommand. No-op when already started.
            startInternal()
        }

        override fun startForegroundService() {
            startForegroundNotification()
        }

        override fun stopForegroundService() {
            stopForegroundNotification()
        }

        override suspend fun startServices() {
            updateNodeServices()
        }

        override suspend fun updateServices() {
            nodeServiceDataSource.updateMobileDataUsage(0)
            updateNodeServices()
        }

        override fun stopServices() {
            mobileNode?.stopProvider()
            analytics.trackEvent(AnalyticsEvent.ToggleAnalyticsEvent.NodeUiState(isEnabled = false))
        }

        override suspend fun restartServices() = bounceProvider()

        override suspend fun stop() {
            isStarted = false
            storage.shouldRun = false
            analytics.trackEvent(AnalyticsEvent.ToggleAnalyticsEvent.NodeUiState(isEnabled = false))
            mobileNode?.stopProvider()
            mobileNode?.shutdown()
        }

        override fun stopSelf() {
            stopSelfService()
        }
    }

}
