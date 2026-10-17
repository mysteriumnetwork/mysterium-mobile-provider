package network.mysterium.node.monitoring

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** What Mysterium's monitoring agent currently thinks of this node. */
enum class MonitoringState { PASSED, FAILED, PENDING, UNKNOWN }

data class MonitoringReport(
    val state: MonitoringState,
    val summary: String,
    val hints: List<String> = emptyList(),
    /** Service types (e.g. "wireguard") the monitoring agent could not reach at all. */
    val failingServices: Set<String> = emptySet(),
    /** Service types the monitoring agent reached successfully. */
    val passingServices: Set<String> = emptySet(),
) {
    /** Text for the foreground notification, or null when there is nothing to say. */
    fun notificationText(): String? = when (state) {
        MonitoringState.FAILED -> (listOf(summary) + hints.take(2)).joinToString("\n")
        MonitoringState.PENDING -> summary
        else -> null
    }
}

/**
 * Explains "Monitoring failed".
 *
 * Two independent signals are combined:
 *  1. Mysterium's public discovery API: a node that passed monitoring is listed
 *     normally, a node that failed is only listed with include_monitoring_failed=true.
 *  2. The node's own local API (monitoring status, per-service monitoring agent
 *     results, NAT type) which tells us WHY it failed. Best effort: if the local
 *     API is not reachable the report simply has fewer details.
 *
 * Every call is wrapped - this class never throws.
 */
class MonitoringDiagnostics {

    private companion object {
        const val TAG = "MonitoringDiagnostics"
        const val DISCOVERY_URL = "https://discovery.mysterium.network/api/v3/proposals"
        const val NODE_API_URL = "http://localhost:4449"
        const val TIMEOUT_MS = 10_000
        const val DEFAULT_USER = "myst"
        const val DEFAULT_PASSWORD = "mystberry"
        const val PUBLIC_SERVICE_ID = "wireguard"
    }

    private class HttpResult(val code: Int, val body: String)

    private class AgentStats(
        val perService: Map<String, Map<String, Int>>,
        val totals: Map<String, Int>,
    )

    @Volatile
    private var authToken: String? = null

    suspend fun check(identityAddress: String, publicServiceEnabled: Boolean): MonitoringReport =
        withContext(Dispatchers.IO) {
            if (identityAddress.isBlank()) {
                return@withContext MonitoringReport(
                    MonitoringState.UNKNOWN,
                    "Node identity is not ready yet."
                )
            }

            val nodeStatus = nodeMonitoringStatus()
            val listed = discoveredProposals(identityAddress, includeFailed = false)
            val listedWithFailed = discoveredProposals(identityAddress, includeFailed = true)

            when (resolveState(nodeStatus, listed, listedWithFailed)) {
                MonitoringState.PASSED -> MonitoringReport(
                    MonitoringState.PASSED,
                    "Monitoring passed."
                )

                MonitoringState.PENDING -> MonitoringReport(
                    MonitoringState.PENDING,
                    "Waiting for Mysterium to verify the node (can take up to 24h, " +
                            "72h on slow connections). Keep it running."
                )

                MonitoringState.UNKNOWN -> MonitoringReport(
                    MonitoringState.UNKNOWN,
                    "Could not read the monitoring status (offline?)."
                )

                MonitoringState.FAILED -> failedReport(publicServiceEnabled)
            }
        }

    private fun resolveState(
        nodeStatus: String?,
        listed: Int?,
        listedWithFailed: Int?,
    ): MonitoringState {
        // The node's own view of the monitoring agent verdict is the most direct signal.
        when (nodeStatus) {
            "success", "passed" -> return MonitoringState.PASSED
            "failed" -> return MonitoringState.FAILED
            "pending" -> return MonitoringState.PENDING
        }
        if (listed != null && listed > 0) return MonitoringState.PASSED
        if (listed != null && listedWithFailed != null) {
            return if (listedWithFailed > 0) MonitoringState.FAILED else MonitoringState.PENDING
        }
        return MonitoringState.UNKNOWN
    }

    private fun failedReport(publicServiceEnabled: Boolean): MonitoringReport {
        val stats = agentStatuses()
        val nat = natType()
        val failing = stats.perService.filter { isFailing(it.value) }.keys
        val passing = stats.perService.filter { (it.value["success"] ?: 0) > 0 }.keys
        val hints = mutableListOf<String>()

        if (failing.isNotEmpty() && passing.isNotEmpty()) {
            hints += "Only ${failing.joinToString()} failed while ${passing.joinToString()} " +
                    "passed - that service is the likely culprit."
        }
        if (PUBLIC_SERVICE_ID in failing && publicServiceEnabled) {
            hints += "Public (wireguard) is failing on this phone. It is switched off " +
                    "automatically if this keeps up."
        } else if (publicServiceEnabled && failing.isEmpty()) {
            hints += "Public (wireguard) is on - if monitoring only started failing after " +
                    "enabling it, it is the prime suspect."
        }

        val connectFail = stats.totals["connect_fail"] ?: 0
        val connectDrop = stats.totals["connect_drop"] ?: 0
        val internetFail = stats.totals["internet_fail"] ?: 0
        if (connectFail > 0) {
            hints += "Monitoring could not reach your node (connect_fail x$connectFail). " +
                    "Usually a network that blocks inbound/NAT traversal: try another Wi-Fi or " +
                    "mobile data and turn off VPN / Private DNS."
        }
        if (connectDrop > 0) {
            hints += "Connections dropped right after connecting (connect_drop x$connectDrop). " +
                    "Usually Wi-Fi/battery sleep or a weak link: keep charging and keep battery " +
                    "optimization off for this app."
        }
        if (internetFail > 0) {
            hints += "The node had no working internet during tests (internet_fail x$internetFail). " +
                    "Check Wi-Fi, DNS and captive portals."
        }
        when (nat) {
            "symmetric" -> hints += "NAT type is symmetric, the hardest to traverse. " +
                    "Try a different network."

            "rcone", "prcone" -> hints += "NAT is restricted ($nat): traversal works but is " +
                    "fragile. Another network or router can help."
        }

        hints += "Status refreshes about hourly. Avoid repeated restarts - they can get you " +
                "rate-limited by Mysterium's quality service."

        return MonitoringReport(
            state = MonitoringState.FAILED,
            summary = "Monitoring failed: Mysterium could not verify your node.",
            hints = hints,
            failingServices = failing,
            passingServices = passing,
        )
    }

    private fun isFailing(counts: Map<String, Int>): Boolean {
        val ok = counts["success"] ?: 0
        val bad = counts.filterKeys { it != "success" && it != "cancelled" }.values.sum()
        return ok == 0 && bad > 0
    }

    // ---- node local API ---------------------------------------------------------------

    private fun nodeMonitoringStatus(): String? {
        val body = nodeGet("/node/monitoring-status") ?: return null
        return try {
            JSONObject(body).optString("status").lowercase().ifBlank { null }
        } catch (error: Exception) {
            null
        }
    }

    private fun natType(): String? {
        val body = nodeGet("/nat/type") ?: return null
        return try {
            JSONObject(body).optString("type").lowercase().ifBlank { null }
        } catch (error: Exception) {
            null
        }
    }

    /**
     * The agent statuses are counts per connectivity status (success / cancelled /
     * connect_drop / connect_fail / internet_fail), grouped per service type. The exact
     * JSON nesting is not guaranteed, so both nested and flat shapes are accepted.
     */
    private fun agentStatuses(): AgentStats {
        val body = nodeGet("/node/monitoring-agent-statuses")
        val statuses = try {
            body?.let { JSONObject(it).optJSONObject("statuses") }
        } catch (error: Exception) {
            null
        } ?: return AgentStats(emptyMap(), emptyMap())

        val perService = linkedMapOf<String, Map<String, Int>>()
        val totals = linkedMapOf<String, Int>()
        val keys = statuses.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = statuses.opt(key)
            if (value is Number) {
                totals[key] = (totals[key] ?: 0) + value.toInt()
            } else if (value is JSONObject) {
                val counts = linkedMapOf<String, Int>()
                val inner = value.keys()
                while (inner.hasNext()) {
                    val status = inner.next()
                    val count = value.opt(status)
                    if (count is Number) {
                        counts[status] = count.toInt()
                        totals[status] = (totals[status] ?: 0) + count.toInt()
                    }
                }
                perService[key] = counts
            }
        }
        return AgentStats(perService, totals)
    }

    private fun nodeGet(path: String): String? {
        return try {
            var response = request("$NODE_API_URL$path", token = authToken)
            if (response.code == 401 || response.code == 403) {
                authToken = authenticate()
                if (authToken != null) {
                    response = request("$NODE_API_URL$path", token = authToken)
                }
            }
            if (response.code in 200..299) response.body else null
        } catch (error: Exception) {
            Log.d(TAG, "node api $path unavailable: ${error.message}")
            null
        }
    }

    private fun authenticate(): String? {
        return try {
            val payload = JSONObject()
                .put("username", DEFAULT_USER)
                .put("password", DEFAULT_PASSWORD)
                .toString()
            val response = request(
                "$NODE_API_URL/auth/authenticate",
                method = "POST",
                body = payload
            )
            if (response.code in 200..299) {
                JSONObject(response.body).optString("token").ifBlank { null }
            } else {
                null
            }
        } catch (error: Exception) {
            null
        }
    }

    // ---- Mysterium discovery API ------------------------------------------------------

    private fun discoveredProposals(identity: String, includeFailed: Boolean): Int? {
        return try {
            val url = StringBuilder(DISCOVERY_URL)
                .append("?provider_id=")
                .append(URLEncoder.encode(identity, "UTF-8"))
            if (includeFailed) url.append("&include_monitoring_failed=true")
            val response = request(url.toString())
            if (response.code !in 200..299) return null
            val text = response.body.trim()
            if (text.startsWith("[")) {
                JSONArray(text).length()
            } else {
                JSONObject(text).optJSONArray("proposals")?.length()
            }
        } catch (error: Exception) {
            Log.d(TAG, "discovery lookup failed: ${error.message}")
            null
        }
    }

    // ---- plain HTTP -------------------------------------------------------------------

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        token: String? = null,
    ): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResult(code, text)
        } finally {
            connection.disconnect()
        }
    }
}
