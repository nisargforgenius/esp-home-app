package com.example.iotcontroller.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.iotcontroller.ui.theme.AppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "settings")

class EspRepository(private val context: Context) {
    // Short timeouts: we're on a local network, and during provisioning we
    // deliberately poll a device that may not be reachable yet -- we don't
    // want each failed attempt to hang for the default 10s+.
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()
    private val wsManager = WebSocketManager(client)
    private val scanner = NetworkScanner(context)
    private val gson = Gson()

    private val IP_KEY = stringPreferencesKey("esp_ip")
    private val SAVED_DEVICES_KEY = stringPreferencesKey("saved_devices")
    private val RELAY_NAMES_KEY = stringPreferencesKey("relay_names")
    private val ROOMS_KEY = stringPreferencesKey("rooms")
    private val THEME_KEY = stringPreferencesKey("app_theme")

    val selectedTheme: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val name = preferences[THEME_KEY]
        AppTheme.entries.find { it.name == name } ?: AppTheme.SLATE_BLUE
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[THEME_KEY] = theme.name }
    }

    val savedIp: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[IP_KEY] ?: "esp-home.local"
    }

    suspend fun saveIp(ip: String) {
        context.dataStore.edit { preferences ->
            preferences[IP_KEY] = ip
        }
    }

    /** Client-side-only relay display names, e.g. "Kitchen Light" -- never sent to the ESP. */
    val relayNames: Flow<List<String>?> = context.dataStore.data.map { preferences ->
        val json = preferences[RELAY_NAMES_KEY] ?: return@map null
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveRelayNames(names: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[RELAY_NAMES_KEY] = gson.toJson(names)
        }
    }

    val savedDevices: Flow<List<SavedDevice>> = context.dataStore.data.map { preferences ->
        val json = preferences[SAVED_DEVICES_KEY] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<SavedDevice>>() {}.type
            gson.fromJson<List<SavedDevice>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Adds or updates a device in the saved list, keyed by IP. */
    suspend fun upsertSavedDevice(device: SavedDevice) {
        val current = savedDevices.first().toMutableList()
        val existingIndex = current.indexOfFirst { it.ip == device.ip }
        if (existingIndex >= 0) {
            current[existingIndex] = device
        } else {
            current.add(device)
        }
        context.dataStore.edit { preferences ->
            preferences[SAVED_DEVICES_KEY] = gson.toJson(current)
        }
    }

    suspend fun removeSavedDevice(ip: String) {
        val current = savedDevices.first().filterNot { it.ip == ip }
        context.dataStore.edit { preferences ->
            preferences[SAVED_DEVICES_KEY] = gson.toJson(current)
        }
    }

    /** Reassigns a device to a room (or null to unassign it). */
    suspend fun assignDeviceToRoom(ip: String, roomId: String?) {
        val current = savedDevices.first().toMutableList()
        val index = current.indexOfFirst { it.ip == ip }
        if (index >= 0) {
            current[index] = current[index].copy(roomId = roomId)
            context.dataStore.edit { preferences ->
                preferences[SAVED_DEVICES_KEY] = gson.toJson(current)
            }
        }
    }

    val rooms: Flow<List<Room>> = context.dataStore.data.map { preferences ->
        val json = preferences[ROOMS_KEY] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<Room>>() {}.type
            gson.fromJson<List<Room>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRoom(name: String): Room {
        val current = rooms.first()
        val newRoom = Room(id = java.util.UUID.randomUUID().toString(), name = name, order = current.size)
        context.dataStore.edit { preferences ->
            preferences[ROOMS_KEY] = gson.toJson(current + newRoom)
        }
        return newRoom
    }

    suspend fun renameRoom(roomId: String, newName: String) {
        val current = rooms.first().toMutableList()
        val index = current.indexOfFirst { it.id == roomId }
        if (index >= 0) {
            current[index] = current[index].copy(name = newName)
            context.dataStore.edit { preferences ->
                preferences[ROOMS_KEY] = gson.toJson(current)
            }
        }
    }

    /** Deletes a room and unassigns any devices that were in it (does not delete the devices). */
    suspend fun deleteRoom(roomId: String) {
        val currentRooms = rooms.first().filterNot { it.id == roomId }
        context.dataStore.edit { preferences ->
            preferences[ROOMS_KEY] = gson.toJson(currentRooms)
        }
        val currentDevices = savedDevices.first().map { if (it.roomId == roomId) it.copy(roomId = null) else it }
        context.dataStore.edit { preferences ->
            preferences[SAVED_DEVICES_KEY] = gson.toJson(currentDevices)
        }
    }

    /**
     * Quick reachability + relay-state check for a single device, used by the
     * home screen to show live ONLINE/OFFLINE per saved device without fully
     * "connecting" (no WebSocket opened, no currentIp/savedIp side effects).
     */
    suspend fun quickCheckDevice(ip: String): EspStatus? {
        return try {
            getStatus(ip)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Scans the phone's current WiFi subnet for ESP-HOME devices. Returns
     * an empty list (rather than throwing) if the phone isn't on WiFi.
     */
    suspend fun scanForDevicesOnNetwork(
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> }
    ): List<SavedDevice> {
        val subnet = scanner.getLocalSubnetPrefix() ?: return emptyList()
        return scanner.scanForDevices(subnet, onProgress)
    }

    /**
     * Finds a device on the current subnet matching the given MAC address
     * (as seen in router/hotspot connected-device lists). Returns null if
     * not found or the phone isn't on WiFi.
     */
    suspend fun findDeviceByMac(
        mac: String,
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> }
    ): SavedDevice? {
        val subnet = scanner.getLocalSubnetPrefix() ?: return null
        return scanner.findDeviceByMac(subnet, mac, onProgress)
    }

    private fun getApiService(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl("http://$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    suspend fun scanNetworks(baseUrl: String) = getApiService(baseUrl).scanNetworks()

    suspend fun connectToWifi(baseUrl: String, request: ConnectRequest) =
        getApiService(baseUrl).connectToWifi(request)

    suspend fun getStatus(baseUrl: String) = getApiService(baseUrl).getStatus()

    suspend fun restartDevice(baseUrl: String) = getApiService(baseUrl).restartDevice()

    suspend fun getSchedules(baseUrl: String) = getApiService(baseUrl).getSchedules()

    suspend fun addSchedule(baseUrl: String, request: AddScheduleRequest) =
        getApiService(baseUrl).addSchedule(request)

    suspend fun deleteSchedule(baseUrl: String, slot: Int) =
        getApiService(baseUrl).deleteSchedule(DeleteScheduleRequest(slot))

    fun connectWebSocket(baseUrl: String) {
        wsManager.connect("ws://$baseUrl:81")
    }

    fun sendRelayCommand(id: Int, state: Boolean) {
        wsManager.sendCommand(WsCommand("relay", id, state))
    }

    fun sendAllCommand(state: Boolean) {
        wsManager.sendCommand(WsCommand("all", state = state))
    }

    val relayEvents = wsManager.events
}
