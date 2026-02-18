package com.example.hastatucasa.ui.cart

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.hastatucasa.data.model.DeliverySlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ─── Dialog ───────────────────────────────────────────────────────────────────

@Composable
fun SlotPickerDialog(
    availableSlots: List<DeliverySlot>,
    onSlotConfirmed: (DeliverySlot) -> Unit,
    onDismiss: () -> Unit,
) {
    // Group slots by date for the date chip row
    val slotsByDate: Map<LocalDate, List<DeliverySlot>> = remember(availableSlots) {
        availableSlots.groupBy { it.date }
    }
    val dates = remember(slotsByDate) { slotsByDate.keys.sorted() }

    var selectedDate by remember { mutableStateOf(dates.firstOrNull()) }
    var selectedSlot by remember { mutableStateOf<DeliverySlot?>(null) }

    // Reset slot selection when the user changes date
    LaunchedEffect(selectedDate) { selectedSlot = null }

    val slotsForSelectedDate = remember(selectedDate, slotsByDate) {
        selectedDate?.let { slotsByDate[it] } ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Schedule Delivery",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )
                }

                // ── Date chips ────────────────────────────────────────────────
                if (dates.isEmpty()) {
                    NoSlotsAvailable()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Pick a date",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.outline,
                            )
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(dates) { date ->
                                DateChip(
                                    date = date,
                                    isSelected = date == selectedDate,
                                    onClick = { selectedDate = date },
                                )
                            }
                        }

                        // ── Slot cards ────────────────────────────────────────
                        if (slotsForSelectedDate.isNotEmpty()) {
                            Text(
                                text = "Pick a time slot",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            )
                            slotsForSelectedDate.forEach { slot ->
                                SlotCard(
                                    slot = slot,
                                    isSelected = slot == selectedSlot,
                                    onClick = { selectedSlot = slot },
                                )
                            }
                        }
                    }

                    // ── Actions ───────────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { selectedSlot?.let(onSlotConfirmed) },
                            enabled = selectedSlot != null,
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

// ─── Date Chip ────────────────────────────────────────────────────────────────

@Composable
private fun DateChip(
    date: LocalDate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isToday = date == LocalDate.now()
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "date_chip_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "date_chip_text",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.width(60.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (isToday) "Today"
                else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = contentColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("MMM")),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = contentColor,
                ),
            )
        }
    }
}

// ─── Slot Card ────────────────────────────────────────────────────────────────

@Composable
private fun SlotCard(
    slot: DeliverySlot,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "slot_border",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "slot_bg",
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor,
        ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = slot.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        )
                    )
                    Text(
                        text = spotsLabel(slot),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (slot.isFull) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                        )
                    )
                }
            }

            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "✓",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun NoSlotsAvailable() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.EventBusy,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "No delivery slots available",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "All upcoming slots are either fully booked\nor past their booking cutoff.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.outline,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun spotsLabel(slot: DeliverySlot): String = when {
    slot.isFull -> "Filling up fast — still accepting orders"
    slot.spotsRemaining <= 3 -> "Only ${slot.spotsRemaining} ${if (slot.spotsRemaining == 1) "spot" else "spots"} left"
    else -> "${slot.spotsRemaining} spots available"
}