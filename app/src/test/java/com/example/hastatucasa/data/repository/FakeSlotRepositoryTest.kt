package com.example.hastatucasa.data.repository

import app.cash.turbine.test
import com.example.hastatucasa.data.model.DeliverySlot
import com.example.hastatucasa.data.model.SlotType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class FakeSlotRepositoryTest {

    private lateinit var repo: FakeSlotRepository

    @Before
    fun setUp() {
        repo = FakeSlotRepository()
    }

    // ── observeAvailableSlots ─────────────────────────────────────────────────

    @Test
    fun `emits at most 2 slots per day (MORNING and AFTERNOON)`() = runTest {
        repo.observeAvailableSlots().test {
            val slots = awaitItem()
            val byDate = slots.groupBy { it.date }
            byDate.values.forEach { daySlots ->
                assertTrue(daySlots.size <= SlotType.entries.size)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits slots for at most AVAILABLE_DAYS distinct dates`() = runTest {
        repo.observeAvailableSlots().test {
            val slots = awaitItem()
            val distinctDates = slots.map { it.date }.distinct()
            assertTrue(distinctDates.size <= DeliverySlot.AVAILABLE_DAYS)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `all emitted slots are within the requested date window`() = runTest {
        val from = LocalDate.now()
        val days = DeliverySlot.AVAILABLE_DAYS
        val windowEnd = from.plusDays(days.toLong())

        repo.observeAvailableSlots(from, days).test {
            val slots = awaitItem()
            slots.forEach { slot ->
                assertTrue("${slot.date} is before window start", !slot.date.isBefore(from))
                assertTrue("${slot.date} is after window end", slot.date.isBefore(windowEnd))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `slots are sorted by date then by SlotType ordinal`() = runTest {
        repo.observeAvailableSlots().test {
            val slots = awaitItem()
            val sortedExpected = slots.sortedWith(compareBy({ it.date }, { it.type }))
            assertEquals(sortedExpected, slots)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `all emitted slots have zero or positive bookedCount`() = runTest {
        repo.observeAvailableSlots().test {
            val slots = awaitItem()
            slots.forEach { slot ->
                assertTrue(slot.bookedCount >= 0)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no slot emitted for a window of zero days`() = runTest {
        repo.observeAvailableSlots(days = 0).test {
            val slots = awaitItem()
            assertTrue(slots.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── bookSlot ──────────────────────────────────────────────────────────────

    @Test
    fun `bookSlot succeeds for a valid slot id`() = runTest {
        var slotId: String? = null
        repo.observeAvailableSlots().test {
            val slots = awaitItem()
            slotId = slots.first().id
            cancelAndIgnoreRemainingEvents()
        }

        val result = repo.bookSlot(slotId!!)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `bookSlot increments bookedCount by 1`() = runTest {
        var slotId: String? = null
        repo.observeAvailableSlots().test {
            val slots = awaitItem()
            slotId = slots.first().id
            cancelAndIgnoreRemainingEvents()
        }

        val before = repo.bookSlot(slotId!!).getOrThrow().bookedCount - 1
        val after = repo.bookSlot(slotId!!).getOrThrow().bookedCount
        assertEquals(before + 2, after)   // two bookings = +2 total from original
    }

    @Test
    fun `bookSlot re-emits updated slot list via flow`() = runTest {
        repo.observeAvailableSlots().test {
            val initial = awaitItem()
            val targetSlot = initial.first()

            repo.bookSlot(targetSlot.id)

            val updated = awaitItem()
            val updatedSlot = updated.first { it.id == targetSlot.id }
            assertEquals(targetSlot.bookedCount + 1, updatedSlot.bookedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bookSlot on a full slot still succeeds (soft limit)`() = runTest {
        var slotId: String? = null
        repo.observeAvailableSlots().test {
            val slots = awaitItem()
            slotId = slots.first().id
            cancelAndIgnoreRemainingEvents()
        }

        // Fill the slot beyond capacity
        repeat(DeliverySlot.SLOT_CAPACITY + 1) { repo.bookSlot(slotId!!) }

        // One more booking must still succeed
        val result = repo.bookSlot(slotId!!)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isFull)
    }

    @Test
    fun `bookSlot returns failure for unknown slot id`() = runTest {
        val result = repo.bookSlot("does-not-exist")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `multiple bookings are cumulative`() = runTest {
        var slotId: String? = null
        repo.observeAvailableSlots().test {
            val slots = awaitItem()
            slotId = slots.first().id
            cancelAndIgnoreRemainingEvents()
        }

        repeat(3) { repo.bookSlot(slotId!!) }

        val final = repo.bookSlot(slotId!!).getOrThrow()
        assertEquals(4, final.bookedCount)
    }

    @Test
    fun `booking one slot does not affect other slots`() = runTest {
        repo.observeAvailableSlots().test {
            val initial = awaitItem()
            if (initial.size < 2) {
                cancelAndIgnoreRemainingEvents()
                return@test
            }
            val first = initial[0]
            val second = initial[1]

            repo.bookSlot(first.id)

            val updated = awaitItem()
            val updatedSecond = updated.first { it.id == second.id }
            assertEquals(second.bookedCount, updatedSecond.bookedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}