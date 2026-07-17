package com.example.iotcontroller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.iotcontroller.ui.components.DeviceScanDialog
import com.example.iotcontroller.ui.components.RelayCard
import com.example.iotcontroller.ui.theme.LedOn
import com.example.iotcontroller.viewmodel.EspViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: EspViewModel, onOpenSchedules: () -> Unit = {}) {
    val uiState = viewModel.uiState
    var showIpDialog by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }
    var renamingIndex by remember { mutableStateOf(-1) }
    var renameText by remember { mutableStateOf("") }
    var tempIp by remember { mutableStateOf(uiState.currentIp) }

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
        // Periodic safety-net poll: the WebSocket carries live relay updates,
        // but if it ever silently drops without triggering onFailure/onClosed
        // (e.g. some Android battery-optimization edge cases), this keeps the
        // dashboard from going stale indefinitely without the user noticing.
        while (true) {
            delay(15000)
            viewModel.refreshStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Automation", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSchedules) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Schedules")
                    }
                    IconButton(onClick = { showScanDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Scan for devices")
                    }
                    IconButton(onClick = { tempIp = uiState.currentIp; showIpDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { viewModel.refreshStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Device Info Header -- IP/Host are read-only here by design; use the
            // Settings gear or the network scan to change which device is targeted.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status: ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (uiState.isConnected) "ONLINE" else "OFFLINE",
                            color = if (uiState.isConnected) Color.Green else Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("IP: ${uiState.currentIp}", style = MaterialTheme.typography.bodySmall)
                    Text("Host: esp-home.local", style = MaterialTheme.typography.bodySmall)

                    if (!uiState.isConnected) {
                        Text(
                            "Use the gear icon to set the IP manually, or scan for devices",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (uiState.deviceStats != null) {
                        Text("Uptime: ${uiState.deviceStats.uptime}s", style = MaterialTheme.typography.bodySmall)
                        Text("Device Time: ${uiState.deviceStats.time ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                        Text("Free Heap: ${uiState.deviceStats.free_heap} bytes", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleAll(true) },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = LedOn)
                ) {
                    Text("ALL ON")
                }
                Button(
                    onClick = { viewModel.toggleAll(false) },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("ALL OFF")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Relay Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(4) { index ->
                    RelayCard(
                        name = uiState.relayNames[index],
                        isOn = uiState.relayStates[index],
                        onToggle = { newState ->
                            if (uiState.isConnected) {
                                viewModel.toggleRelay(index + 1, newState)
                            }
                        },
                        onRename = {
                            renamingIndex = index
                            renameText = uiState.relayNames[index]
                        }
                    )
                }
            }
            
            // Footer Action
            Button(
                onClick = { showRestartConfirm = true },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = uiState.isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Icon(Icons.Default.PowerSettingsNew, null)
                Spacer(Modifier.width(8.dp))
                Text("RESTART DEVICE")
            }
        }
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("Restart Device?") },
            text = { Text("The device will disconnect briefly and reboot. All relays keep their wiring but will reset to a known state on boot.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    viewModel.restartDevice()
                }) {
                    Text("Restart")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Set ESP IP Address") },
            text = {
                Column {
                    Text("Enter the IP shown in your Serial Monitor, or use the scan (search icon) instead", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = tempIp,
                        onValueChange = { tempIp = it },
                        label = { Text("IP Address") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setManualIp(tempIp)
                    showIpDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (renamingIndex >= 0) {
        AlertDialog(
            onDismissRequest = { renamingIndex = -1 },
            title = { Text("Rename Relay") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameRelay(renamingIndex, renameText)
                    renamingIndex = -1
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingIndex = -1 }) {
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
            }
        )
    }
}
