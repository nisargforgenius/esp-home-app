package com.example.iotcontroller.data

data class WifiNetwork(
    val ssid: String,
    val rssi: Int,
    val enc: String
)

data class ConnectRequest(
    val ssid: String,
    val password: String
)

data class ConnectResponse(
    val status: String,
    val ip: String? = null
)

data class EspStatus(
    val r1: Int,
    val r2: Int,
    val r3: Int,
    val r4: Int,
    val uptime: Long,
    val free_heap: Long,
    val rssi: Int,
    val ip: String,
    val mac: String? = null,
    val time: String? = null // New field to debug time sync on device
)

data class WsCommand(
    val cmd: String,
    val id: Int? = null,
    val state: Boolean? = null
)

data class WsEvent(
    val event: String,
    val id: Int,
    val state: Boolean
)

data class SavedDevice(
    val ip: String,
    val name: String = "ESP Relay Controller",
    val lastSeenUptime: Long = 0L,
    val roomId: String? = null, // null = unassigned, not yet sorted into a room
    val mac: String? = null
)

data class Room(
    val id: String,
    val name: String,
    val order: Int = 0
)

// --- SCHEDULING ---
// action: 1 = turn relay on, 0 = turn relay off (matches firmware convention).
data class ScheduleItem(
    val slot: Int,
    val relayId: Int,
    val hour: Int,
    val minute: Int,
    val action: Int,
    val firedToday: Boolean = false
)

data class AddScheduleRequest(
    val relayId: Int,
    val hour: Int,
    val minute: Int,
    val action: Int
)

data class AddScheduleResponse(
    val status: String,
    val slot: Int? = null
)

data class DeleteScheduleRequest(
    val slot: Int
)
