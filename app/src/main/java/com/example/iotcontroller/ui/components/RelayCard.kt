package com.example.iotcontroller.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iotcontroller.ui.theme.LedOff
import com.example.iotcontroller.ui.theme.LedOn

@Composable
fun RelayCard(
    name: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ledColor by animateColorAsState(if (isOn) LedOn else LedOff, label = "led")
    // surfaceVariant (not a fixed translucent white) so the card actually
    // contrasts against the background on both dark themes and Sky Light.
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant
    val nameColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // weight(1f) + the fixed-width controls on the right sharing the
            // remaining space is what keeps a long relay name from pushing the
            // LED/switch out of the card -- without this, an unbounded Row
            // just grows past the card edge and wraps unpredictably.
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = nameColor,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onRename, modifier = Modifier.size(20.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = mutedColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = if (isOn) "ENABLED" else "DISABLED",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOn) LedOn else mutedColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // LED Indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(ledColor, Color.Transparent)
                            )
                        )
                        .background(ledColor.copy(alpha = 0.5f))
                )
                
                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = isOn,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LedOn,
                        checkedTrackColor = LedOn.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
