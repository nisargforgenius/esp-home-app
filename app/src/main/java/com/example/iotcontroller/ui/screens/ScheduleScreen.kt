package com.example.iotcontroller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iotcontroller.data.ScheduleItem
import com.example.iotcontroller.ui.theme.LedOn
import com.example.iotcontroller.viewmodel.EspViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: EspViewModel, onBack: () -> Unit) {
    val uiState = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    var slotPendingDelete by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadSchedules()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                // Firmware caps schedules at 10 slots -- keep the affordance to
                // add more disabled once full, with an explanatory tap-through.
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add schedule")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "These run on the device itself -- schedules keep working even if " +
                    "your phone is off or the app is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            uiState.scheduleError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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

            if (uiState.isLoadingSchedules) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.schedules.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No schedules yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                LazyColumn {
                    items(uiState.schedules.sortedBy { it.hour * 60 + it.minute }) { schedule ->
                        ScheduleRow(
                            schedule = schedule,
                            relayName = uiState.relayNames.getOrElse(schedule.relayId - 1) { "Relay ${schedule.relayId}" },
                            onDelete = { slotPendingDelete = schedule.slot }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            relayNames = uiState.relayNames,
            onDismiss = { showAddDialog = false },
            onConfirm = { relayIds, hour, minute, turnOn ->
                viewModel.addSchedulesForRelays(relayIds, hour, minute, turnOn) { successCount, failureMessage ->
                    if (failureMessage == null) {
                        showAddDialog = false
                    }
                    // On partial/total failure we leave the dialog open; the
                    // error (including how many succeeded before it failed)
                    // is surfaced via uiState.scheduleError already.
                }
            }
        )
    }

    slotPendingDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { slotPendingDelete = null },
            title = { Text("Delete this schedule?") },
            text = { Text("This removes it from the device permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSchedule(slot)
                    slotPendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { slotPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScheduleRow(schedule: ScheduleItem, relayName: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(relayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (schedule.firedToday) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Fired today",
                            tint = LedOn,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = String.format(Locale.US, "%02d:%02d", schedule.hour, schedule.minute) +
                        " -> " + if (schedule.action == 1) "Turn ON" else "Turn OFF",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (schedule.action == 1) LedOn else MaterialTheme.colorScheme.error
                )
                if (schedule.firedToday) {
                    Text(
                        text = "Fired today \u2014 will run again tomorrow",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete schedule", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleDialog(
    relayNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (relayIds: Set<Int>, hour: Int, minute: Int, turnOn: Boolean) -> Unit
) {
    // Multi-select: e.g. picking relays 1, 3 lets one schedule turn off both
    // at once, same as manually tapping them one after another on the
    // dashboard -- just automated. Each selected relay becomes its own
    // schedule slot on the device (no firmware/EEPROM change needed for
    // this), the multi-select here is purely a faster way to create several
    // at once from one dialog.
    var selectedRelays by remember { mutableStateOf(setOf(1)) }
    var turnOn by remember { mutableStateOf(true) }
    val timePickerState = rememberTimePickerState(initialHour = 18, initialMinute = 0, is24Hour = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Schedule") },
        text = {
            Column {
                Text("Relays", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))

                // Using a Column with Row wrapping to prevent the overlapping/vertical 
                // chip bug seen in the screenshot when relay names are long.
                val rows = relayNames.withIndex().toList().chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { rowEntries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowEntries.forEach { (index, name) ->
                                val relayId = index + 1
                                FilterChip(
                                    selected = relayId in selectedRelays,
                                    onClick = {
                                        selectedRelays = if (relayId in selectedRelays) {
                                            // Keep at least one relay selected -- an empty
                                            // set would silently create no schedule at all.
                                            if (selectedRelays.size > 1) selectedRelays - relayId else selectedRelays
                                        } else {
                                            selectedRelays + relayId
                                        }
                                    },
                                    label = { 
                                        Text(
                                            text = name,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) 
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // If a row has only 1 item, add a spacer to keep weights consistent
                            if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (selectedRelays.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${selectedRelays.size} relays selected \u2014 creates one schedule per relay",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Action", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = turnOn, onClick = { turnOn = true }, label = { Text("Turn ON") })
                    FilterChip(selected = !turnOn, onClick = { turnOn = false }, label = { Text("Turn OFF") })
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Time", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                // TimeInput (compact text-field style), not TimePicker (the
                // dial-face version): the dial is meant for full-width/
                // full-screen contexts, and inside this narrow AlertDialog
                // column its hour/minute hand rendered past the dial's own
                // bounds. TimeInput has no dial to overflow and fits cleanly
                // in a constrained dialog.
                TimeInput(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selectedRelays, timePickerState.hour, timePickerState.minute, turnOn)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
