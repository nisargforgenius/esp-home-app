package com.example.iotcontroller.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.iotcontroller.data.SavedDevice
import com.example.iotcontroller.viewmodel.EspUiState
import com.example.iotcontroller.viewmodel.EspViewModel

/**
 * Shared "scan for ESP devices on this network" dialog, used from both the
 * setup screen (before finishing guided setup) and the dashboard (after).
 * Scanning here is independent of the WiFi-provisioning flow -- it just
 * probes the phone's current subnet for a device that responds to /status
 * with the expected shape, so it can be used any time the phone is already
 * on the same WiFi as a previously-provisioned device.
 */
@Composable
fun DeviceScanDialog(
    uiState: EspUiState,
    viewModel: EspViewModel,
    onDismiss: () -> Unit,
    onDeviceSelected: (SavedDevice) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!uiState.isScanning) onDismiss() },
        title = { Text("Scan for ESP Devices") },
        text = {
            Column {
                if (uiState.isScanning) {
                    Text(
                        "Checking your network... (${uiState.scanProgress.first}/${uiState.scanProgress.second})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (uiState.scanProgress.second > 0) uiState.scanProgress.first.toFloat() / uiState.scanProgress.second else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (uiState.scanResults.isEmpty()) {
                    Text(
                        "Tap Scan to search your current WiFi network for ESP-HOME devices.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Found ${uiState.scanResults.size} device(s). Tap one to connect:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.scanResults.forEach { device ->
                        ListItem(
                            headlineContent = { Text(device.name) },
                            supportingContent = { Text(device.ip) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceSelected(device) }
                        )
                    }
                }

                if (uiState.savedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Saved devices:", style = MaterialTheme.typography.labelMedium)
                    uiState.savedDevices.forEach { device ->
                        ListItem(
                            headlineContent = { Text(device.name) },
                            supportingContent = { Text(device.ip) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceSelected(device) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.scanForDevices() },
                enabled = !uiState.isScanning
            ) {
                Text("Scan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.isScanning) {
                Text("Close")
            }
        }
    )
}
