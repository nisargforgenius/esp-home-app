package com.example.iotcontroller.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.iotcontroller.data.Room

/**
 * Shown right after a device is successfully added/connected for the first
 * time, so the user picks its room immediately instead of finding it under
 * "Unassigned" later. Never guesses a room automatically -- always an
 * explicit choice, with "Skip for now" leaving it unassigned (same end state
 * as not using this dialog at all).
 */
@Composable
fun RoomPickerDialog(
    deviceName: String,
    rooms: List<Room>,
    onRoomSelected: (roomId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add \"$deviceName\" to a room?") },
        text = {
            Column {
                if (rooms.isEmpty()) {
                    Text("You don't have any rooms yet. You can create one and organize devices later from the home screen.")
                } else {
                    rooms.forEach { room ->
                        ListItem(
                            headlineContent = { Text(room.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRoomSelected(room.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip for now")
            }
        }
    )
}
