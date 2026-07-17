package com.example.iotcontroller.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotcontroller.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val AP_IP = "192.168.4.1"
private const val MDNS_HOST = "esp-home.local"
private const val STATIC_ESP_IP = "10.185.42.200" // Matches firmware STATIC_IP -- update both together if you change networks

// How long (and how often) to poll for the device to reappear on the home network
// after we hand it new WiFi credentials and it reboots.
private const val PROVISION_POLL_ATTEMPTS = 12
private const val PROVISION_POLL_DELAY_MS = 2000L

class EspViewModel(private val repository: EspRepository) : ViewModel() {

    var uiState by mutableStateOf(EspUiState())
        private set

    init {
        viewModelScope.launch {
            repository.savedIp.collectLatest { ip ->
                uiState = uiState.copy(currentIp = ip ?: STATIC_ESP_IP)
            }
        }

        viewModelScope.launch {
            repository.relayEvents.collectLatest { event ->
                val newStates = uiState.relayStates.toMutableList()
                if (event.id in 1..4) {
                    newStates[event.id - 1] = event.state
                    uiState = uiState.copy(relayStates = newStates)
                }
            }
        }

        viewModelScope.launch {
            repository.relayNames.collectLatest { names ->
                if (names != null) uiState = uiState.copy(relayNames = names)
            }
        }

        viewModelScope.launch {
            repository.savedDevices.collectLatest { devices ->
                uiState = uiState.copy(savedDevices = devices)
            }
        }

        viewModelScope.launch {
            repository.rooms.collectLatest { rooms ->
                uiState = uiState.copy(rooms = rooms)
            }
        }
    }

    /**
     * Scans the current subnet for a device matching the given MAC address,
     * e.g. one copied from a router's connected-devices screen. On success,
     * connects to it like any other found device.
     */
    fun findDeviceByMac(mac: String, onResult: (found: SavedDevice?) -> Unit) {
        viewModelScope.launch {
            uiState = uiState.copy(isScanning = true, scanProgress = 0 to 254, error = null)
            val device = repository.findDeviceByMac(mac) { checked, total ->
                uiState = uiState.copy(scanProgress = checked to total)
            }
            uiState = uiState.copy(isScanning = false)
            if (device == null) {
                uiState = uiState.copy(error = "No device with that MAC address found on this network.")
            }
            onResult(device)
        }
    }

    /** Assigns a device to a room right after it's added (or null to leave unassigned). */
    fun assignDeviceToRoom(ip: String, roomId: String?) {
        viewModelScope.launch {
            repository.assignDeviceToRoom(ip, roomId)
        }
    }

    /**
     * Fetches the current schedule list straight from the ESP -- this is
     * always ground truth (not app-local state), since the device is the
     * one that actually persists and executes schedules.
     */
    fun loadSchedules() {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoadingSchedules = true, scheduleError = null)
                val schedules = repository.getSchedules(uiState.currentIp)
                uiState = uiState.copy(schedules = schedules, isLoadingSchedules = false)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingSchedules = false,
                    scheduleError = "Couldn't load schedules from the device. Check it's online."
                )
            }
        }
    }

    fun addSchedule(relayId: Int, hour: Int, minute: Int, turnOn: Boolean, onResult: (success: Boolean, message: String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = repository.addSchedule(
                    uiState.currentIp,
                    AddScheduleRequest(relayId = relayId, hour = hour, minute = minute, action = if (turnOn) 1 else 0)
                )
                if (response.status == "added") {
                    loadSchedules()
                    onResult(true, null)
                } else {
                    onResult(false, "Device rejected the schedule.")
                }
            } catch (e: Exception) {
                // The ESP returns 409 when its 10-slot table is full -- surface
                // that distinctly since it's an actionable, expected case.
                val message = if (e.message?.contains("409") == true) {
                    "This device already has 10 schedules (the max). Delete one first."
                } else {
                    "Couldn't reach the device to add the schedule."
                }
                onResult(false, message)
            }
        }
    }

    // Creates one schedule slot per selected relay, sequentially. Each relay
    // becomes its own independent schedule on the device -- there's no
    // firmware-level "multi-relay" slot, so e.g. picking relays 1 and 3
    // for "turn off at 11pm" just writes two schedules instead of one.
    // Because the device only has 10 slots total, a multi-relay pick can
    // partially succeed (e.g. 2 of 4 relays before the table fills) --
    // this reports that clearly rather than silently losing track of it.
    fun addSchedulesForRelays(
        relayIds: Set<Int>,
        hour: Int,
        minute: Int,
        turnOn: Boolean,
        onResult: (successCount: Int, failureMessage: String?) -> Unit
    ) {
        viewModelScope.launch {
            var successCount = 0
            var failureMessage: String? = null
            for (relayId in relayIds.sorted()) {
                try {
                    val response = repository.addSchedule(
                        uiState.currentIp,
                        AddScheduleRequest(relayId = relayId, hour = hour, minute = minute, action = if (turnOn) 1 else 0)
                    )
                    if (response.status == "added") {
                        successCount++
                    } else {
                        failureMessage = "Device rejected the schedule for relay $relayId."
                        break
                    }
                } catch (e: Exception) {
                    failureMessage = if (e.message?.contains("409") == true) {
                        if (successCount > 0) {
                            "Added $successCount of ${relayIds.size} schedules, then ran out of slots (10 max). Delete one to add the rest."
                        } else {
                            "This device already has 10 schedules (the max). Delete one first."
                        }
                    } else {
                        "Couldn't reach the device to add the schedule."
                    }
                    break
                }
            }
            loadSchedules()
            if (failureMessage != null) {
                uiState = uiState.copy(scheduleError = failureMessage)
            }
            onResult(successCount, failureMessage)
        }
    }

    fun deleteSchedule(slot: Int) {
        viewModelScope.launch {
            try {
                repository.deleteSchedule(uiState.currentIp, slot)
                loadSchedules()
            } catch (e: Exception) {
                uiState = uiState.copy(scheduleError = "Couldn't delete -- check the device is online and try again.")
            }
        }
    }

    /**
     * Called once on app launch. Tries the last-known IP with a short timeout
     * to see if the device is still reachable, so returning users can skip
     * setup entirely instead of re-doing it every time they open the app.
     * Returns true if found and connected, false otherwise (caller should
     * route to the setup screen in that case).
     */
    suspend fun tryReconnectToSavedDevice(): Boolean {
        // Wait for the saved IP to actually load from DataStore before trying --
        // uiState starts with the STATIC_ESP_IP default, so without this we could
        // race and try the wrong address on first collection.
        val savedIp = repository.savedIp.first()
        if (savedIp.isNullOrBlank()) return false
        uiState = uiState.copy(currentIp = savedIp)
        return tryConnectTo(savedIp)
    }

    /** Renames a relay locally (client-side only -- never sent to the ESP). */
    fun renameRelay(index: Int, newName: String) {
        viewModelScope.launch {
            val updated = uiState.relayNames.toMutableList()
            if (index in updated.indices && newName.isNotBlank()) {
                updated[index] = newName.trim()
                uiState = uiState.copy(relayNames = updated)
                repository.saveRelayNames(updated)
            }
        }
    }

    /**
     * Scans the current WiFi subnet for ESP-HOME devices by probing every host
     * address for a /status response matching the expected shape. Takes a few
     * seconds since it's checking up to 254 addresses (concurrently).
     */
    fun scanForDevices() {
        viewModelScope.launch {
            uiState = uiState.copy(isScanning = true, scanResults = emptyList(), scanProgress = 0 to 254, error = null)
            val found = repository.scanForDevicesOnNetwork { checked, total ->
                uiState = uiState.copy(scanProgress = checked to total)
            }
            uiState = uiState.copy(isScanning = false, scanResults = found)
            if (found.isEmpty()) {
                uiState = uiState.copy(error = "No ESP-HOME devices found on this network. Make sure the device is powered on and connected to the same WiFi as this phone.")
            }
        }
    }

    /** Connects to a device found via scan (or picked from the saved list) and saves it for next time. */
    fun connectToDevice(device: SavedDevice) {
        viewModelScope.launch {
            repository.upsertSavedDevice(device)
            setManualIp(device.ip)
        }
    }

    fun forgetDevice(ip: String) {
        viewModelScope.launch {
            repository.removeSavedDevice(ip)
        }
    }

    fun scanNetworks() {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, error = null)
                val networks = repository.scanNetworks(AP_IP)
                uiState = uiState.copy(networks = networks, isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Couldn't reach the device. Make sure your phone is connected to the ESP-HOME-xxxx WiFi network."
                )
            }
        }
    }

    /**
     * Step 1: sends new WiFi credentials to the device while the phone is still
     * joined to the ESP's own setup AP. Must be called BEFORE the phone switches
     * networks, or the POST has nowhere to go. Fire-and-forget by design: the ESP
     * typically drops the AP connection immediately after accepting this and
     * restarting, so a failure/timeout here is expected, not a real error.
     */
    fun sendCredentials(ssid: String, pass: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, connectionStatus = "Sending WiFi details...")
            try {
                repository.connectToWifi(AP_IP, ConnectRequest(ssid, pass))
            } catch (e: Exception) {
                // Expected -- see doc comment above.
            }
            uiState = uiState.copy(connectionStatus = "Device is restarting and joining your WiFi...")
        }
    }

    /**
     * Step 2: called once the user confirms their phone is back on the home
     * network. Polls for the device to appear. If it can't be found automatically
     * within the timeout, the caller (ConnectScreen) should route the user to
     * manual IP entry instead of navigating blind into a dashboard that will just
     * show OFFLINE.
     *
     * @param onResult called once with true (found + connected) or false (timed out)
     */
    fun waitForDeviceOnline(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)

            var found = false
            repeat(PROVISION_POLL_ATTEMPTS) { attempt ->
                if (!found) {
                    uiState = uiState.copy(
                        connectionStatus = "Looking for device on your network... (${attempt + 1}/$PROVISION_POLL_ATTEMPTS)"
                    )
                    // Static IP is the primary target since firmware now requests
                    // a fixed address; mDNS is kept as a fallback in case the
                    // network config ever changes without updating both sides.
                    found = tryConnectTo(STATIC_ESP_IP)
                    if (!found) found = tryConnectTo(MDNS_HOST)
                    if (!found) delay(PROVISION_POLL_DELAY_MS)
                }
            }

            uiState = uiState.copy(
                isLoading = false,
                connectionStatus = if (found) "Connected!" else "Couldn't find the device automatically."
            )
            onResult(found)
        }
    }

    /** Tries a single status check against the given host; updates state and returns success. */
    private suspend fun tryConnectTo(host: String): Boolean {
        return try {
            val status = repository.getStatus(host)
            uiState = uiState.copy(
                relayStates = listOf(status.r1 == 1, status.r2 == 1, status.r3 == 1, status.r4 == 1),
                deviceStats = status,
                isConnected = true,
                currentIp = status.ip,
                error = null
            )
            repository.saveIp(status.ip)
            repository.connectWebSocket(status.ip)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val ok = tryConnectTo(uiState.currentIp)
            if (!ok) {
                uiState = uiState.copy(
                    isConnected = false,
                    error = "Can't reach ${uiState.currentIp}. Check the device is powered on and on the same network, or set its IP manually."
                )
            }
        }
    }

    fun setManualIp(ip: String) {
        viewModelScope.launch {
            uiState = uiState.copy(currentIp = ip, error = null)
            repository.saveIp(ip)
            // Manual entry must also land in the saved-devices list, the same
            // as scan-found devices -- otherwise the device never shows up on
            // the Home screen even though the dashboard connects fine, which
            // is exactly the "no devices yet after connecting" bug. If this IP
            // is already saved (e.g. reconnecting), preserve its existing name
            // and room instead of resetting them to defaults.
            val existing = repository.savedDevices.first().find { it.ip == ip }
            repository.upsertSavedDevice(existing ?: SavedDevice(ip = ip))
            refreshStatus()
        }
    }

    fun toggleRelay(id: Int, state: Boolean) {
        repository.sendRelayCommand(id, state)
    }

    fun toggleAll(state: Boolean) {
        repository.sendAllCommand(state)
    }

    fun restartDevice() {
        viewModelScope.launch {
            try {
                repository.restartDevice(uiState.currentIp)
                uiState = uiState.copy(isConnected = false, connectionStatus = "Restarting...")
            } catch (e: Exception) {
                // Device typically drops the connection immediately on restart,
                // so a failure here is expected rather than a real error.
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }
}

data class EspUiState(
    val currentIp: String = STATIC_ESP_IP,
    val relayStates: List<Boolean> = listOf(false, false, false, false),
    val relayNames: List<String> = listOf("Living Room Light", "Bedroom Fan", "Kitchen Light", "Garden Pump"),
    val networks: List<WifiNetwork> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionStatus: String = "Disconnected",
    val isConnected: Boolean = false,
    val deviceStats: EspStatus? = null,
    val savedDevices: List<SavedDevice> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val schedules: List<ScheduleItem> = emptyList(),
    val isLoadingSchedules: Boolean = false,
    val scheduleError: String? = null,
    val isScanning: Boolean = false,
    val scanProgress: Pair<Int, Int> = 0 to 0,
    val scanResults: List<SavedDevice> = emptyList()
)
