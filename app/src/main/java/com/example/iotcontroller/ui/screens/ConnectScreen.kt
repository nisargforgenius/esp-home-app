package com.example.iotcontroller.ui.screens

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.iotcontroller.ui.components.DeviceScanDialog
import com.example.iotcontroller.ui.components.RoomPickerDialog
import com.example.iotcontroller.viewmodel.EspViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(viewModel: EspViewModel, onNavigateToDashboard: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState = viewModel.uiState
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }
    var selectedSsid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var manualIp by remember { mutableStateOf("") }
    var manualMac by remember { mutableStateOf("") }
    var useMacEntry by remember { mutableStateOf(false) }
    var showReconnectPrompt by remember { mutableStateOf(false) }
    var pendingSsid by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf("") }
    // Set whenever a device is successfully added, so we can show the room
    // picker once before actually navigating to the dashboard.
    var pendingRoomPickerIp by remember { mutableStateOf<String?>(null) }
    var pendingRoomPickerName by remember { mutableStateOf("ESP Device") }
    // Set right before we send the user to WiFi settings, so we know to
    // auto-rescan when they come back instead of making them tap the button again.
    var awaitingReturnFromWifiSettings by remember { mutableStateOf(false) }

    // Auto-rescan when the app resumes after the user manually joined the
    // ESP's setup hotspot in system WiFi settings -- closes the "come back and
    // tap the button again" gap in the guided setup flow.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingReturnFromWifiSettings) {
                awaitingReturnFromWifiSettings = false
                viewModel.scanNetworks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup") },
                actions = {
                    // Lets a returning user who already provisioned a device on this
                    // network jump straight to it, without repeating the AP-join /
                    // credential-entry flow just to get back to the dashboard.
                    IconButton(onClick = { showScanDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Scan for devices already on this network")
                    }
                }
            )
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Connect to ESP Device",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Follow the steps to set up your IoT Controller",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (uiState.networks.isEmpty()) {
            Button(
                onClick = {
                    awaitingReturnFromWifiSettings = true
                    checkWifiAndStart(context, viewModel)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                enabled = !uiState.isLoading
            ) {
                Text("Start Guided Setup")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (awaitingReturnFromWifiSettings) {
                TextButton(onClick = {
                    awaitingReturnFromWifiSettings = false
                    viewModel.scanNetworks()
                }) {
                    Text("I'm connected to the device WiFi -- Scan now")
                }
            }

            TextButton(onClick = { showManualIpDialog = true }) {
                Text("Already provisioned? Enter IP manually")
            }
        } else {
            Text(
                text = "Available Wi-Fi Networks",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )

            LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                items(uiState.networks) { network ->
                    ListItem(
                        headlineContent = { Text(network.ssid) },
                        supportingContent = { Text("${network.rssi} dBm | ${network.enc}") },
                        trailingContent = { Icon(Icons.Default.Wifi, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedSsid = network.ssid
                                showPasswordDialog = true
                            },
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        if (uiState.connectionStatus != "Disconnected") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.connectionStatus,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Connect to $selectedSsid") },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    pendingSsid = selectedSsid
                    pendingPassword = password
                    // Fire while the phone is still on the ESP's setup AP --
                    // this is the only window in which 192.168.4.1 is reachable.
                    viewModel.sendCredentials(selectedSsid, password)
                    showReconnectPrompt = true
                }) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReconnectPrompt) {
        AlertDialog(
            onDismissRequest = { /* force a choice -- don't let this dismiss accidentally */ },
            title = { Text("One More Step") },
            text = {
                Text(
                    "The device has your WiFi details and is restarting. Your phone is still " +
                        "connected to the device's temporary \"$pendingSsid\"-setup network, which " +
                        "will disappear now.\n\nPlease switch your phone back to \"$pendingSsid\" " +
                        "(your home WiFi), then tap Done."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }) {
                    Text("Open WiFi Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReconnectPrompt = false
                    viewModel.waitForDeviceOnline { found ->
                        if (found) {
                            pendingRoomPickerName = "ESP Device"
                            pendingRoomPickerIp = viewModel.uiState.currentIp
                        } else {
                            showManualIpDialog = true
                        }
                    }
                }) {
                    Text("Done, I'm Reconnected")
                }
            }
        )
    }

    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            title = { Text(if (useMacEntry) "Enter Device MAC Address" else "Enter Device IP") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !useMacEntry, onClick = { useMacEntry = false }, label = { Text("IP Address") })
                        FilterChip(selected = useMacEntry, onClick = { useMacEntry = true }, label = { Text("MAC Address") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (useMacEntry) {
                        Text(
                            "Check your router or hotspot's connected-devices list for the MAC " +
                                "address, then paste it here. We'll scan this WiFi network to find " +
                                "the matching device.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualMac,
                            onValueChange = { manualMac = it },
                            label = { Text("MAC Address (e.g. 8C:CE:4E:E9:5D:36)") },
                            singleLine = true
                        )
                        if (uiState.isScanning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Scanning... (${uiState.scanProgress.first}/${uiState.scanProgress.second})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(
                                progress = { if (uiState.scanProgress.second > 0) uiState.scanProgress.first.toFloat() / uiState.scanProgress.second else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            "Couldn't find the device automatically. Check your router's connected " +
                                "devices list, or the Serial Monitor, for the IP address.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("IP Address") },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isScanning,
                    onClick = {
                        if (useMacEntry) {
                            viewModel.findDeviceByMac(manualMac) { found ->
                                if (found != null) {
                                    showManualIpDialog = false
                                    viewModel.connectToDevice(found)
                                    pendingRoomPickerName = found.name
                                    pendingRoomPickerIp = found.ip
                                }
                                // On failure, dialog stays open with uiState.error
                                // shown via the existing error banner in ConnectScreen.
                            }
                        } else {
                            showManualIpDialog = false
                            viewModel.setManualIp(manualIp)
                            pendingRoomPickerName = "ESP Device"
                            pendingRoomPickerIp = manualIp
                        }
                    }
                ) {
                    Text(if (useMacEntry) "Find & Connect" else "Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showScanDialog) {
        DeviceScanDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showScanDialog = false },
            onDeviceSelected = { device ->
                viewModel.connectToDevice(device)
                showScanDialog = false
                pendingRoomPickerName = device.name
                pendingRoomPickerIp = device.ip
            }
        )
    }

    pendingRoomPickerIp?.let { ip ->
        RoomPickerDialog(
            deviceName = pendingRoomPickerName,
            rooms = uiState.rooms,
            onRoomSelected = { roomId ->
                viewModel.assignDeviceToRoom(ip, roomId)
                pendingRoomPickerIp = null
                onNavigateToDashboard()
            },
            onDismiss = {
                pendingRoomPickerIp = null
                onNavigateToDashboard()
            }
        )
    }
}

/**
 * Kicks off guided setup by sending the user to system WiFi settings, since
 * Android apps can't join an arbitrary WiFi network (like ESP-HOME-xxxx) on
 * the user's behalf -- they must pick it manually. If WiFi itself is off, we
 * open the quick panel to turn it on first. ConnectScreen listens for the app
 * resuming afterward and automatically re-scans for the device.
 */
fun checkWifiAndStart(context: Context, viewModel: EspViewModel) {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            try {
                context.startActivity(Intent(Settings.Panel.ACTION_WIFI))
            } catch (e: Exception) {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        } else {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    } catch (e: Exception) {
        Log.e("ConnectScreen", "Error starting setup: ${e.message}", e)
    }
}
