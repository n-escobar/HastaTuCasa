package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.DeliverySlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// ─── Interface ────────────────────────────────────────────────────────────────

interface SlotRepository {
    /**
     * Emits the list of bookable slots starting from [from] for [days] consecutive days.
     * Slots whose booking cutoff has already passed are excluded from each emission.
     * The flow re-emits whenever a slot's [DeliverySlot.bookedCount] changes (e.g. after booking).
     */
    fun observeAvailableSlots(
        from: LocalDate = LocalDate.now(),
        days: Int = DeliverySlot.AVAILABLE_DAYS,
    ): Flow<List<DeliverySlot>>

    /**
     * Increments the bookedCount for [slotId].
     * Capacity is a soft limit — succeeds even when [DeliverySlot.isFull] is true.
     * Returns failure only when [slotId] is not found.
     */
    suspend fun bookSlot(slotId: String): Result<DeliverySlot>
}

// ─── Fake Implementation ──────────────────────────────────────────────────────

@Singleton
class FakeSlotRepository @Inject constructor() : SlotRepository {

    /**
     * Source of truth: all slots for the current 7-day window, keyed by id.
     * Generated eagerly at construction so tests have a stable set to assert against.
     */
    private val _slots = MutableStateFlow(generateInitialSlots())

    override fun observeAvailableSlots(
        from: LocalDate,
        days: Int,
    ): Flow<List<DeliverySlot>> {
        val windowDates = (0 until days).map { from.plusDays(it.toLong()) }.toSet()
        return _slots.map { allSlots ->
            val now = Instant.now()
            allSlots.values
                .filter { slot ->
                    slot.date in windowDates && slot.isBookable(now)
                }
                .sortedWith(compareBy({ it.date }, { it.type }))
        }
    }

    override suspend fun bookSlot(slotId: String): Result<DeliverySlot> {
        val current = _slots.value[slotId]
            ?: return Result.failure(IllegalArgumentException("Slot not found: $slotId"))

        val updated = current.copy(bookedCount = current.bookedCount + 1)
        _slots.update { it + (slotId to updated) }
        return Result.success(updated)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateInitialSlots(): Map<String, DeliverySlot> {
        val today = LocalDate.now()
        return (0 until DeliverySlot.AVAILABLE_DAYS)
            .flatMap { offset -> DeliverySlot.generateForDate(today.plusDays(offset.toLong())) }
            .associateBy { it.id }
    }
}