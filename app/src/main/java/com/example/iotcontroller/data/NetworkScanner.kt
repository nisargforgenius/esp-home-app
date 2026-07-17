package com.example.iotcontroller.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Discovers ESP-HOME devices on the phone's current local WiFi subnet by
 * probing every host address for a /status response that matches the
 * expected shape (r1-r4, uptime, ip, etc). A device only counts as "found"
 * if its response actually parses as EspStatus -- this is what distinguishes
 * a genuine ESP-HOME controller from any other device that happens to have
 * port 80 open (a printer, a router's admin page, and so on).
 */
class NetworkScanner(private val context: Context) {

    // Short timeouts are essential here: we're about to fire up to 254
    // concurrent requests, most of which will hit nothing and should fail
    // fast rather than hang.
    private val scanClient = OkHttpClient.Builder()
        .connectTimeout(400, TimeUnit.MILLISECONDS)
        .readTimeout(600, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Returns the phone's own subnet prefix, e.g. "10.185.42" for a phone at
     * 10.185.42.205/24. Returns null if not connected to WiFi or the address
     * can't be determined.
     */
    fun getLocalSubnetPrefix(): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        
        // Try getting IP from WifiManager (for when phone is a client)
        val ipInt = wifiManager.connectionInfo?.ipAddress ?: 0
        
        // If 0, phone might be a hotspot. Try parsing from network interfaces.
        if (ipInt == 0) {
            try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    // Hotspot interface on Android is usually 'ap0', 'wlan1', or similar
                    if (intf.name.contains("ap") || intf.name.contains("wlan")) {
                        val addrs = intf.inetAddresses
                        while (addrs.hasMoreElements()) {
                            val addr = addrs.nextElement()
                            if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                                val host = addr.hostAddress ?: continue
                                return host.substringBeforeLast(".")
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
            return null
        }

        // WifiManager reports the IP in little-endian order on most devices.
        val ip = String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
        return ip.substringBeforeLast(".")
    }

    /**
     * Probes every address in the given /24 subnet (1-254) for a live
     * ESP-HOME device. Runs all probes concurrently on IO dispatchers.
     * Progress callback reports (checked, total) so the UI can show a
     * live "checked 40/254" style indicator.
     */
    suspend fun scanForDevices(
        subnetPrefix: String,
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> }
    ): List<SavedDevice> = withContext(Dispatchers.IO) {
        val total = 254
        var checkedCount = 0

        val results = coroutineScope {
            (1..254).map { host ->
                async {
                    val ip = "$subnetPrefix.$host"
                    val device = probeAddress(ip)
                    synchronized(this@NetworkScanner) {
                        checkedCount++
                        onProgress(checkedCount, total)
                    }
                    device
                }
            }.awaitAll()
        }

        results.filterNotNull()
    }

    /**
     * Same as scanForDevices, but matches against a specific MAC address --
     * useful when the user already knows which device they're after (e.g.
     * from their router's connected-devices list) rather than picking from
     * a full list of results. All 254 probes still run concurrently (there's
     * no way to "stop early" once they're all in flight), but the UI can
     * show the match the moment it's found rather than waiting to display
     * a full results list. MAC comparison is case-insensitive and
     * colon-format-tolerant.
     */
    suspend fun findDeviceByMac(
        subnetPrefix: String,
        targetMac: String,
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> }
    ): SavedDevice? = withContext(Dispatchers.IO) {
        val normalizedTarget = normalizeMac(targetMac)
        if (normalizedTarget.isBlank()) return@withContext null

        val total = 254
        var checkedCount = 0
        var found: SavedDevice? = null

        coroutineScope {
            val jobs = (1..254).map { host ->
                async {
                    val ip = "$subnetPrefix.$host"
                    val device = probeAddress(ip)
                    synchronized(this@NetworkScanner) {
                        checkedCount++
                        onProgress(checkedCount, total)
                        if (found == null && device?.mac != null && normalizeMac(device.mac) == normalizedTarget) {
                            found = device
                        }
                    }
                    device
                }
            }
            jobs.awaitAll()
        }

        found
    }

    /** Strips separators and lowercases so "8C:CE:4E:E9:5D:36" == "8cce4ee95d36". */
    private fun normalizeMac(mac: String): String =
        mac.replace(":", "").replace("-", "").lowercase()

    private fun probeAddress(ip: String): SavedDevice? {
        return try {
            val request = Request.Builder()
                .url("http://$ip/status")
                .build()
            scanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val status = com.google.gson.Gson().fromJson(body, EspStatus::class.java)
                // A malformed/unrelated JSON response will deserialize with
                // null/default fields rather than throwing -- explicitly check
                // that the fields we actually care about are present and sane.
                if (status != null && status.ip.isNotBlank()) {
                    SavedDevice(ip = ip, lastSeenUptime = status.uptime, mac = status.mac)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
