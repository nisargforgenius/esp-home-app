package com.example.iotcontroller.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotcontroller.data.EspRepository
import com.example.iotcontroller.data.EspStatus
import com.example.iotcontroller.data.Room
import com.example.iotcontroller.data.SavedDevice
import com.example.iotcontroller.ui.theme.AppTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class DeviceWithStatus(
    val device: SavedDevice,
    val isOnline: Boolean,
    val status: EspStatus? = null
)

data class HomeUiState(
    val rooms: List<Room> = emptyList(),
    val devicesByRoom: Map<String?, List<DeviceWithStatus>> = emptyMap(), // null key = unassigned
    val isCheckingStatus: Boolean = false,
    val appTheme: AppTheme = AppTheme.SLATE_BLUE
)

/**
 * Drives the home screen: rooms, the devices within each room (including an
 * "unassigned" bucket for devices not yet sorted into a room), and a live
 * ONLINE/OFFLINE check for each saved device. Deliberately separate from
 * EspViewModel, which owns the state of whichever single device the user is
 * currently viewing on the dashboard -- this screen is a lightweight overview
 * across all saved devices at once.
 */
class HomeViewModel(private val repository: EspRepository) : ViewModel() {

    var uiState by mutableStateOf(HomeUiState())
        private set

    private var allDevices: List<SavedDevice> = emptyList()

    init {
        viewModelScope.launch {
            repository.rooms.collectLatest { rooms ->
                uiState = uiState.copy(rooms = rooms.sortedBy { it.order })
            }
        }

        viewModelScope.launch {
            repository.selectedTheme.collectLatest { theme ->
                uiState = uiState.copy(appTheme = theme)
            }
        }

        viewModelScope.launch {
            repository.savedDevices.collectLatest { devices ->
                allDevices = devices
                // Rebuild grouping immediately with whatever status info we already
                // have, then kick off a fresh status check.
                regroup(devices, uiState.devicesByRoom.values.flatten().associateBy { it.device.ip })
                checkAllStatuses()
            }
        }
    }

    private fun regroup(devices: List<SavedDevice>, statusByIp: Map<String, DeviceWithStatus>) {
        val grouped = devices
            .map { device ->
                statusByIp[device.ip] ?: DeviceWithStatus(device = device, isOnline = false)
            }
            .groupBy { it.device.roomId }
        uiState = uiState.copy(devicesByRoom = grouped)
    }

    /** Pings every saved device concurrently to refresh ONLINE/OFFLINE + live stats. */
    fun checkAllStatuses() {
        viewModelScope.launch {
            if (allDevices.isEmpty()) return@launch
            uiState = uiState.copy(isCheckingStatus = true)

            val results = coroutineScope {
                allDevices.map { device ->
                    async {
                        val status = repository.quickCheckDevice(device.ip)
                        DeviceWithStatus(device = device, isOnline = status != null, status = status)
                    }
                }.awaitAll()
            }

            val statusByIp = results.associateBy { it.device.ip }
            regroup(allDevices, statusByIp)
            uiState = uiState.copy(isCheckingStatus = false)
        }
    }

    fun addRoom(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) repository.addRoom(name.trim())
        }
    }

    fun renameRoom(roomId: String, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) repository.renameRoom(roomId, newName.trim())
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            repository.deleteRoom(roomId)
        }
    }

    fun assignDeviceToRoom(ip: String, roomId: String?) {
        viewModelScope.launch {
            repository.assignDeviceToRoom(ip, roomId)
        }
    }

    fun removeDevice(ip: String) {
        viewModelScope.launch {
            repository.removeSavedDevice(ip)
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            repository.setTheme(theme)
        }
    }
}
