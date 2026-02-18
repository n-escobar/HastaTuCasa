package com.example.hastatucasa.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DeliverySlotTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 2, 17)   // fixed date for determinism

    // ── init validation ───────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `negative bookedCount throws`() {
        slot(bookedCount = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero capacity throws`() {
        slot(capacity = 0)
    }

    // ── isFull ────────────────────────────────────────────────────────────────

    @Test
    fun `isFull is false when bookedCount is below capacity`() {
        assertFalse(slot(bookedCount = 9, capacity = 10).isFull)
    }

    @Test
    fun `isFull is true when bookedCount equals capacity`() {
        assertTrue(slot(bookedCount = 10, capacity = 10).isFull)
    }

    @Test
    fun `isFull is true when bookedCount exceeds capacity (soft limit overflow)`() {
        assertTrue(slot(bookedCount = 11, capacity = 10).isFull)
    }

    // ── spotsRemaining ────────────────────────────────────────────────────────

    @Test
    fun `spotsRemaining returns difference when not full`() {
        assertEquals(3, slot(bookedCount = 7, capacity = 10).spotsRemaining)
    }

    @Test
    fun `spotsRemaining is zero when full`() {
        assertEquals(0, slot(bookedCount = 10, capacity = 10).spotsRemaining)
    }

    @Test
    fun `spotsRemaining floors at zero when over capacity`() {
        assertEquals(0, slot(bookedCount = 12, capacity = 10).spotsRemaining)
    }

    // ── isBookable ────────────────────────────────────────────────────────────

    @Test
    fun `isBookable is true when more than 2 hours before slot starts`() {
        // Morning slot starts at 08:00 UTC; now = 05:00 → 3 hours before cutoff
        val now = zonedInstant(today, 5, 0)
        assertTrue(slot(type = SlotType.MORNING).isBookable(now, zone))
    }

    @Test
    fun `isBookable is false when exactly at the 2-hour cutoff`() {
        // Morning starts 08:00, cutoff is 06:00; now = exactly 06:00
        val now = zonedInstant(today, 6, 0)
        assertFalse(slot(type = SlotType.MORNING).isBookable(now, zone))
    }

    @Test
    fun `isBookable is false when inside the 2-hour cutoff window`() {
        // Morning starts 08:00, cutoff is 06:00; now = 07:00
        val now = zonedInstant(today, 7, 0)
        assertFalse(slot(type = SlotType.MORNING).isBookable(now, zone))
    }

    @Test
    fun `isBookable is false for a slot in the past`() {
        val yesterday = today.minusDays(1)
        val now = zonedInstant(today, 9, 0)
        assertFalse(slot(date = yesterday, type = SlotType.MORNING).isBookable(now, zone))
    }

    @Test
    fun `isBookable works correctly for AFTERNOON slot`() {
        // Afternoon starts 12:00, cutoff is 10:00; now = 09:59
        val now = zonedInstant(today, 9, 59)
        assertTrue(slot(type = SlotType.AFTERNOON).isBookable(now, zone))
    }

    @Test
    fun `isBookable is false for AFTERNOON when past cutoff`() {
        // Afternoon starts 12:00, cutoff is 10:00; now = 11:00
        val now = zonedInstant(today, 11, 0)
        assertFalse(slot(type = SlotType.AFTERNOON).isBookable(now, zone))
    }

    // ── startTime / endTime ───────────────────────────────────────────────────

    @Test
    fun `startTime for MORNING is 08-00 UTC`() {
        val expected = zonedInstant(today, 8, 0)
        assertEquals(expected, slot(type = SlotType.MORNING).startTime(zone))
    }

    @Test
    fun `endTime for MORNING is 12-00 UTC`() {
        val expected = zonedInstant(today, 12, 0)
        assertEquals(expected, slot(type = SlotType.MORNING).endTime(zone))
    }

    @Test
    fun `startTime for AFTERNOON is 12-00 UTC`() {
        val expected = zonedInstant(today, 12, 0)
        assertEquals(expected, slot(type = SlotType.AFTERNOON).startTime(zone))
    }

    @Test
    fun `endTime for AFTERNOON is 17-00 UTC`() {
        val expected = zonedInstant(today, 17, 0)
        assertEquals(expected, slot(type = SlotType.AFTERNOON).endTime(zone))
    }

    // ── label ─────────────────────────────────────────────────────────────────

    @Test
    fun `label for MORNING contains 'Morning'`() {
        assertTrue(slot(type = SlotType.MORNING).label.contains("Morning"))
    }

    @Test
    fun `label for AFTERNOON contains 'Afternoon'`() {
        assertTrue(slot(type = SlotType.AFTERNOON).label.contains("Afternoon"))
    }

    @Test
    fun `label for MORNING contains hour range`() {
        val label = slot(type = SlotType.MORNING).label
        assertTrue("Expected '8:00 AM' in label: $label", label.contains("8:00 AM"))
        assertTrue("Expected '12:00 PM' in label: $label", label.contains("12:00 PM"))
    }

    @Test
    fun `label for AFTERNOON contains hour range`() {
        val label = slot(type = SlotType.AFTERNOON).label
        assertTrue("Expected '12:00 PM' in label: $label", label.contains("12:00 PM"))
        assertTrue("Expected '5:00 PM' in label: $label", label.contains("5:00 PM"))
    }

    // ── idFor / generateForDate ───────────────────────────────────────────────

    @Test
    fun `idFor produces deterministic id`() {
        assertEquals("2026-02-17_MORNING", DeliverySlot.idFor(today, SlotType.MORNING))
        assertEquals("2026-02-17_AFTERNOON", DeliverySlot.idFor(today, SlotType.AFTERNOON))
    }

    @Test
    fun `generateForDate produces one slot per SlotType`() {
        val slots = DeliverySlot.generateForDate(today)
        assertEquals(SlotType.entries.size, slots.size)
        assertTrue(slots.any { it.type == SlotType.MORNING })
        assertTrue(slots.any { it.type == SlotType.AFTERNOON })
    }

    @Test
    fun `generateForDate slots all have correct date`() {
        DeliverySlot.generateForDate(today).forEach { slot ->
            assertEquals(today, slot.date)
        }
    }

    @Test
    fun `generateForDate slots start with zero bookedCount`() {
        DeliverySlot.generateForDate(today).forEach { slot ->
            assertEquals(0, slot.bookedCount)
        }
    }

    // ── SlotType properties ───────────────────────────────────────────────────

    @Test
    fun `MORNING startHour is 8 and endHour is 12`() {
        assertEquals(8, SlotType.MORNING.startHour)
        assertEquals(12, SlotType.MORNING.endHour)
    }

    @Test
    fun `AFTERNOON startHour is 12 and endHour is 17`() {
        assertEquals(12, SlotType.AFTERNOON.startHour)
        assertEquals(17, SlotType.AFTERNOON.endHour)
    }

    @Test
    fun `cutoffHours is 2 for all slot types`() {
        SlotType.entries.forEach { type ->
            assertEquals(2L, type.cutoffHours)
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun slot(
        date: LocalDate = today,
        type: SlotType = SlotType.MORNING,
        bookedCount: Int = 0,
        capacity: Int = 10,
    ) = DeliverySlot(
        id = DeliverySlot.idFor(date, type),
        date = date,
        type = type,
        bookedCount = bookedCount,
        capacity = capacity,
    )

    private fun zonedInstant(date: LocalDate, hour: Int, minute: Int): Instant =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant()
}