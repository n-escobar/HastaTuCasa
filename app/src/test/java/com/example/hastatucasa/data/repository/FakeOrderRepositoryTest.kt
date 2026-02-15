package com.example.hastatucasa.data.repository

import app.cash.turbine.test
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.ProductUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Tests for FakeOrderRepository.
 *
 * Dependencies needed in build.gradle.kts (test scope):
 *   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
 *   testImplementation("app.cash.turbine:turbine:1.1.0")
 *   testImplementation("junit:junit:4.13.2")
 */
class FakeOrderRepositoryTest {
    //18 TESTS IN THIS CLASS
    private lateinit var repo: FakeOrderRepository

    @Before
    fun setUp() {
        repo = FakeOrderRepository()
    }

    // ── observeOrdersForShopper ───────────────────────────────────────────────

    @Test
    fun `observeOrdersForShopper returns only orders for that shopper`() = runTest {
        repo.observeOrdersForShopper("user-shopper").test {
            val orders = awaitItem()
            assertTrue(orders.all { it.shopperId == "user-shopper" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeOrdersForShopper returns empty list for unknown shopper`() = runTest {
        repo.observeOrdersForShopper("nobody").test {
            val orders = awaitItem()
            assertTrue(orders.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeOrdersForShopper orders are sorted newest first`() = runTest {
        repo.observeOrdersForShopper("user-shopper").test {
            val orders = awaitItem()
            val timestamps = orders.map { it.createdAt }
            assertEquals(timestamps.sortedDescending(), timestamps)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeOrdersForShopper emits update after placeOrder`() = runTest {
        repo.observeOrdersForShopper("user-shopper").test {
            val initial = awaitItem()
            val initialCount = initial.size

            repo.placeOrder(newOrder("ord-new", "user-shopper"))

            val updated = awaitItem()
            assertEquals(initialCount + 1, updated.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    fun `getOrder returns order when it exists`() = runTest {
        val order = repo.getOrder("ord-001")
        assertNotNull(order)
        assertEquals("ord-001", order?.orderId)
    }

    @Test
    fun `getOrder returns null for unknown id`() = runTest {
        assertNull(repo.getOrder("does-not-exist"))
    }

    // ── placeOrder ────────────────────────────────────────────────────────────

    @Test
    fun `placeOrder succeeds and order is retrievable`() = runTest {
        val order = newOrder("ord-new", "user-shopper")
        val result = repo.placeOrder(order)

        assertTrue(result.isSuccess)
        assertEquals(order, result.getOrNull())
        assertEquals(order, repo.getOrder("ord-new"))
    }

    @Test
    fun `placeOrder for different shopper does not appear in other shopper flow`() = runTest {
        repo.observeOrdersForShopper("user-shopper").test {
            awaitItem() // drain initial

            repo.placeOrder(newOrder("ord-other", "user-other"))

            // No new emission for user-shopper
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    fun `cancelOrder on PENDING order succeeds`() = runTest {
        // ord-002 is PENDING in sample data
        val result = repo.cancelOrder("ord-002", "Changed my mind")

        assertTrue(result.isSuccess)
        assertEquals(OrderStatus.CANCELLED, result.getOrNull()?.status)
    }

    @Test
    fun `cancelOrder adds a status history entry`() = runTest {
        val result = repo.cancelOrder("ord-002", "Test reason")
        val order = result.getOrThrow()

        val lastChange = order.statusHistory.last()
        assertEquals(OrderStatus.CANCELLED, lastChange.toStatus)
        assertEquals("Test reason", lastChange.reason)
    }

    @Test
    fun `cancelOrder on DELIVERED order fails`() = runTest {
        // ord-003 is DELIVERED
        val result = repo.cancelOrder("ord-003", "Too late")
        assertTrue(result.isFailure)
    }

    @Test
    fun `cancelOrder on non-existent order fails`() = runTest {
        val result = repo.cancelOrder("ord-ghost", "reason")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────────

    @Test
    fun `valid status transition succeeds`() = runTest {
        // ord-002 is PENDING → can go to CONFIRMED
        val result = repo.updateOrderStatus("ord-002", OrderStatus.CONFIRMED, "system")

        assertTrue(result.isSuccess)
        assertEquals(OrderStatus.CONFIRMED, result.getOrNull()?.status)
    }

    @Test
    fun `invalid status transition fails with IllegalStateException`() = runTest {
        // ord-002 is PENDING → cannot skip to PREPARING
        val result = repo.updateOrderStatus("ord-002", OrderStatus.PREPARING, "system")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `updateOrderStatus on unknown order fails with IllegalArgumentException`() = runTest {
        val result = repo.updateOrderStatus("ghost", OrderStatus.CONFIRMED, "system")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `updateOrderStatus appends to statusHistory`() = runTest {
        val before = repo.getOrder("ord-002")!!
        val historySizeBefore = before.statusHistory.size

        val result = repo.updateOrderStatus("ord-002", OrderStatus.CONFIRMED, "system")
        val after = result.getOrThrow()

        assertEquals(historySizeBefore + 1, after.statusHistory.size)
        val newEntry = after.statusHistory.last()
        assertEquals(OrderStatus.PENDING, newEntry.fromStatus)
        assertEquals(OrderStatus.CONFIRMED, newEntry.toStatus)
        assertEquals("system", newEntry.changedBy)
    }

    @Test
    fun `updated order is observable via flow`() = runTest {
        repo.observeOrdersForShopper("user-shopper").test {
            awaitItem() // drain initial

            repo.updateOrderStatus("ord-002", OrderStatus.CONFIRMED, "system")

            val updated = awaitItem()
            val ord002 = updated.first { it.orderId == "ord-002" }
            assertEquals(OrderStatus.CONFIRMED, ord002.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `full lifecycle transitions succeed in sequence`() = runTest {
        val steps = listOf(
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED,
        )
        // ord-002 starts as PENDING
        steps.forEach { nextStatus ->
            val result = repo.updateOrderStatus("ord-002", nextStatus, "system")
            assertTrue("Transition to $nextStatus failed", result.isSuccess)
        }
        assertEquals(OrderStatus.DELIVERED, repo.getOrder("ord-002")?.status)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun newOrder(orderId: String, shopperId: String) = Order(
        orderId = orderId,
        shopperId = shopperId,
        items = listOf(
            OrderItem("p1", "Apple", ProductUnit.LB, BigDecimal("1.99"), BigDecimal("1"))
        ),
        status = OrderStatus.PENDING,
        orderType = OrderType.ON_DEMAND,
        deliveryAddress = "1 Test Ave",
        createdAt = Instant.now(),
    )
}