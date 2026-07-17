package com.example.iotcontroller.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.iotcontroller.data.SavedDevice
import com.example.iotcontroller.ui.components.DeviceScanDialog
import com.example.iotcontroller.ui.components.RoomPickerDialog
import com.example.iotcontroller.viewmodel.DeviceWithStatus
import com.example.iotcontroller.viewmodel.EspViewModel
import com.example.iotcontroller.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    espViewModel: EspViewModel, // needed for the shared scan dialog's scan/connect actions
    onOpenDevice: (SavedDevice) -> Unit,
    onStartGuidedSetup: () -> Unit,
    onEnterIpManually: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState = homeViewModel.uiState
    val espUiState = espViewModel.uiState
    var showScanDialog by remember { mutableStateOf(false) }
    var showAddRoomDialog by remember { mutableStateOf(false) }
    var newRoomName by remember { mutableStateOf("") }
    var expandedRooms by remember { mutableStateOf(setOf<String?>()) }
    var deviceToAssign by remember { mutableStateOf<SavedDevice?>(null) }
    var pendingRoomPickerDevice by remember { mutableStateOf<SavedDevice?>(null) }

    LaunchedEffect(Unit) {
        homeViewModel.checkAllStatuses()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Automation", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAddRoomDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Room")
                    }
                    IconButton(onClick = { showScanDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Scan for devices")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onEnterIpManually,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Enter IP Manually")
                }
                Button(
                    onClick = onStartGuidedSetup,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Guided Setup")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.rooms.isEmpty() && uiState.devicesByRoom.values.flatten().isEmpty()) {
                // True empty state -- no rooms, no devices at all yet.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "No devices yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Scan for a device already on this WiFi, or start guided setup below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAddRoomDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Room")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(uiState.rooms) { room ->
                        val devicesInRoom = uiState.devicesByRoom[room.id].orEmpty()
                        val isExpanded = room.id in expandedRooms || expandedRooms.isEmpty() && uiState.rooms.size == 1

                        RoomSection(
                            title = room.name,
                            deviceCount = devicesInRoom.size,
                            isExpanded = room.id in expandedRooms,
                            onToggleExpand = {
                                expandedRooms = if (room.id in expandedRooms) {
                                    expandedRooms - room.id
                                } else {
                                    expandedRooms + room.id
                                }
                            },
                            onDeleteRoom = { homeViewModel.deleteRoom(room.id) }
                        )

                        if (room.id in expandedRooms) {
                            devicesInRoom.forEach { deviceStatus ->
                                DeviceRow(
                                    deviceStatus = deviceStatus,
                                    onClick = { onOpenDevice(deviceStatus.device) },
                                    onLongPress = { deviceToAssign = deviceStatus.device }
                                )
                            }
                        }
                    }

                    val unassigned = uiState.devicesByRoom[null].orEmpty()
                    if (unassigned.isNotEmpty()) {
                        item {
                            RoomSection(
                                title = "Unassigned",
                                deviceCount = unassigned.size,
                                isExpanded = null in expandedRooms,
                                onToggleExpand = {
                                    expandedRooms = if (null in expandedRooms) {
                                        expandedRooms - null
                                    } else {
                                        expandedRooms + null
                                    }
                                },
                                onDeleteRoom = null
                            )
                        }
                        if (null in expandedRooms) {
                            items(unassigned) { deviceStatus ->
                                DeviceRow(
                                    deviceStatus = deviceStatus,
                                    onClick = { onOpenDevice(deviceStatus.device) },
                                    onLongPress = { deviceToAssign = deviceStatus.device }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showAddRoomDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Room")
                        }
                    }
                }
            }
        }
    }

    if (showAddRoomDialog) {
        AlertDialog(
            onDismissRequest = { showAddRoomDialog = false },
            title = { Text("Add Room") },
            text = {
                OutlinedTextField(
                    value = newRoomName,
                    onValueChange = { newRoomName = it },
                    label = { Text("Room name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.addRoom(newRoomName)
                    newRoomName = ""
                    showAddRoomDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRoomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    deviceToAssign?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToAssign = null },
            title = { Text("Move \"${device.name}\" to...") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Unassigned") },
                        modifier = Modifier.clickable {
                            homeViewModel.assignDeviceToRoom(device.ip, null)
                            deviceToAssign = null
                        }
                    )
                    uiState.rooms.forEach { room ->
                        ListItem(
                            headlineContent = { Text(room.name) },
                            modifier = Modifier.clickable {
                                homeViewModel.assignDeviceToRoom(device.ip, room.id)
                                deviceToAssign = null
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.removeDevice(device.ip)
                    deviceToAssign = null
                }) {
                    Text("Forget Device", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToAssign = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showScanDialog) {
        DeviceScanDialog(
            uiState = espUiState,
            viewModel = espViewModel,
            onDismiss = { showScanDialog = false },
            onDeviceSelected = { scannedDevice ->
                espViewModel.connectToDevice(scannedDevice)
                showScanDialog = false
                pendingRoomPickerDevice = scannedDevice
            }
        )
    }

    pendingRoomPickerDevice?.let { device ->
        RoomPickerDialog(
            deviceName = device.name,
            rooms = uiState.rooms,
            onRoomSelected = { roomId ->
                homeViewModel.assignDeviceToRoom(device.ip, roomId)
                pendingRoomPickerDevice = null
                homeViewModel.checkAllStatuses()
            },
            onDismiss = {
                pendingRoomPickerDevice = null
                homeViewModel.checkAllStatuses()
            }
        )
    }
}

@Composable
private fun RoomSection(
    title: String,
    deviceCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDeleteRoom: (() -> Unit)?
) {
    // Chevron rotates smoothly instead of swapping icon assets -- reads as
    // one continuous motion rather than a jarring flip, and doubles as the
    // main visual cue that the whole bubble is tappable.
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "$deviceCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            if (onDeleteRoom != null) {
                TextButton(onClick = onDeleteRoom) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun DeviceRow(
    deviceStatus: DeviceWithStatus,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceStatus.device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "IP: ${deviceStatus.device.ip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = if (deviceStatus.isOnline) "ONLINE" else "OFFLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (deviceStatus.isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
            }
            // Long-press substitute: a small "move" affordance, since Compose
            // combinedClickable adds complexity for a dialog that's simple
            // enough to expose directly.
            TextButton(onClick = onLongPress) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Move to room")
            }
        }
    }
}
