package com.example.hastatucasa.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// ─── Slot Type ────────────────────────────────────────────────────────────────

enum class SlotType(
    val startHour: Int,
    val endHour: Int,
    val cutoffHours: Long = 2L,
) {
    MORNING(startHour = 8, endHour = 12),
    AFTERNOON(startHour = 12, endHour = 17);

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercaseChar() }
}

// ─── Domain Model ─────────────────────────────────────────────────────────────

data class DeliverySlot(
    val id: String,                     // e.g. "2026-02-17_MORNING"
    val date: LocalDate,
    val type: SlotType,
    val bookedCount: Int = 0,
    val capacity: Int = SLOT_CAPACITY,
) {
    init {
        require(bookedCount >= 0) { "bookedCount cannot be negative: $bookedCount" }
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    // ── Computed properties ───────────────────────────────────────────────────

    /** Instant at which this slot opens for delivery. */
    fun startTime(zone: ZoneId = ZoneId.systemDefault()): Instant =
        ZonedDateTime.of(date, LocalTime.of(type.startHour, 0), zone).toInstant()

    /** Instant at which this slot closes for delivery. */
    fun endTime(zone: ZoneId = ZoneId.systemDefault()): Instant =
        ZonedDateTime.of(date, LocalTime.of(type.endHour, 0), zone).toInstant()

    /**
     * Returns true if the shopper can still book this slot given [now].
     * Booking closes [SlotType.cutoffHours] before the slot starts.
     */
    fun isBookable(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val cutoff = startTime(zone).minusSeconds(type.cutoffHours * 3_600)
        return now.isBefore(cutoff)
    }

    /** True when bookedCount has reached capacity. Does NOT block booking (soft limit). */
    val isFull: Boolean get() = bookedCount >= capacity

    /** Remaining spots, floored at zero for display purposes. */
    val spotsRemaining: Int get() = maxOf(0, capacity - bookedCount)

    /** Human-readable label shown in the slot picker. */
    val label: String
        get() = "${type.displayName} · ${hourLabel(type.startHour)} – ${hourLabel(type.endHour)}"

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        const val SLOT_CAPACITY = 10
        const val CUTOFF_HOURS = 2L
        const val AVAILABLE_DAYS = 7

        fun idFor(date: LocalDate, type: SlotType): String = "${date}_${type.name}"

        fun generateForDate(date: LocalDate): List<DeliverySlot> =
            SlotType.entries.map { type ->
                DeliverySlot(
                    id = idFor(date, type),
                    date = date,
                    type = type,
                )
            }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun hourLabel(hour: Int): String =
    LocalTime.of(hour, 0).format(timeFormatter)