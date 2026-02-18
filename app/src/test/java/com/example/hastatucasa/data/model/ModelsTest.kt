package com.example.hastatucasa.data.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class ProductTest {
    // 10 TESTS IN THIS CLASS
    // ── effectivePrice ────────────────────────────────────────────────────────

    @Test
    fun `effectivePrice equals price when no discount`() {
        val product = product(price = "5.00", discount = "0")
        assertEquals(BigDecimal("5.00"), product.effectivePrice)
    }

    @Test
    fun `effectivePrice applies 10 percent discount correctly`() {
        val product = product(price = "10.00", discount = "10")
        assertEquals(BigDecimal("9.00"), product.effectivePrice)
    }

    @Test
    fun `effectivePrice applies 15 percent discount and rounds half-up`() {
        // 3.99 * 0.85 = 3.3915 → rounds to 3.39
        val product = product(price = "3.99", discount = "15")
        assertEquals(BigDecimal("3.39"), product.effectivePrice)
    }

    @Test
    fun `effectivePrice is zero when price is zero`() {
        val product = product(price = "0.00", discount = "20")
        assertEquals(BigDecimal("0.00"), product.effectivePrice)
    }

    @Test
    fun `effectivePrice is zero when discount is 100 percent`() {
        val product = product(price = "9.99", discount = "100")
        assertEquals(BigDecimal("0.00"), product.effectivePrice)
    }

    // ── hasDiscount ───────────────────────────────────────────────────────────

    @Test
    fun `hasDiscount is false when discount is zero`() {
        assertFalse(product(discount = "0").hasDiscount)
    }

    @Test
    fun `hasDiscount is true when discount is positive`() {
        assertTrue(product(discount = "10").hasDiscount)
    }

    // ── init validation ───────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `negative price throws`() {
        product(price = "-0.01")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `discount above 100 throws`() {
        product(discount = "100.01")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative discount throws`() {
        product(discount = "-1")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun product(
        price: String = "1.00",
        discount: String = "0",
        unit: ProductUnit = ProductUnit.PIECE,
    ) = Product(
        id = "p1", name = "Test", price = BigDecimal(price),
        imageUrl = "", category = "Test", unit = unit,
        discountPercent = BigDecimal(discount),
    )
}


// ─────────────────────────────────────────────────────────────────────────────

class OrderItemTest {
    // 9 TESTS IN THIS CLASS
    @Test
    fun `subtotal is price times quantity`() {
        val item = item(price = "3.59", qty = "2")
        assertEquals(BigDecimal("7.18"), item.subtotal)
    }

    @Test
    fun `subtotal scales to 2 decimal places`() {
        val item = item(price = "3.99", qty = "3")
        assertEquals(BigDecimal("11.97"), item.subtotal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero quantity throws`() {
        item(qty = "0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative quantity throws`() {
        item(qty = "-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fractional quantity for PIECE product throws`() {
        item(qty = "1.5", unit = ProductUnit.PIECE)
    }

    @Test
    fun `fractional quantity for LB product is allowed`() {
        val item = item(qty = "1.5", unit = ProductUnit.LB)
        assertEquals(BigDecimal("1.5"), item.quantity)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative price throws`() {
        item(price = "-0.01")
    }

    private fun item(
        price: String = "5.00",
        qty: String = "1",
        unit: ProductUnit = ProductUnit.PIECE,
    ) = OrderItem(
        productId = "p1", productName = "Test", productUnit = unit,
        priceAtPurchase = BigDecimal(price), quantity = BigDecimal(qty),
    )
}


// ─────────────────────────────────────────────────────────────────────────────

class OrderTest {
    // 7 TESTS IN THIS CLASS
    @Test
    fun `totalPrice sums item subtotals`() {
        val order = order(
            items = listOf(
                orderItem("3.59", "2"),   // 7.18
                orderItem("5.49", "1"),   // 5.49
                orderItem("5.99", "1"),   // 5.99
            )
        )
        assertEquals(BigDecimal("18.66"), order.totalPrice)
    }

    @Test
    fun `totalPrice with single item`() {
        val order = order(items = listOf(orderItem("10.00", "3")))
        assertEquals(BigDecimal("30.00"), order.totalPrice)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty items list throws`() {
        order(items = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank delivery address throws`() {
        order(address = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scheduled order without scheduledFor throws`() {
        order(type = OrderType.SCHEDULED, scheduledFor = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `on-demand order with scheduledFor throws`() {
        order(type = OrderType.ON_DEMAND, scheduledFor = Instant.now())
    }

    @Test
    fun `scheduled order with scheduledFor is valid`() {
        val o = order(type = OrderType.SCHEDULED, scheduledFor = Instant.now())
        assertEquals(OrderType.SCHEDULED, o.orderType)
    }

    @Test
    fun `deliveryFee equals the domain constant`() {
        val order = order()
        assertEquals(Order.DELIVERY_FEE, order.deliveryFee)
    }

    @Test
    fun `grandTotal is totalPrice plus deliveryFee`() {
        val order = order(
            items = listOf(
                orderItem("3.59", "2"),  // 7.18
                orderItem("5.49", "1"),  // 5.49
            )
        )
        // totalPrice = 12.67, deliveryFee = 3.99, grandTotal = 16.66
        val expected = order.totalPrice.add(Order.DELIVERY_FEE)
            .setScale(2, RoundingMode.HALF_UP)
        assertEquals(expected, order.grandTotal)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun orderItem(price: String, qty: String) = OrderItem(
        productId = "p1", productName = "Test", productUnit = ProductUnit.PIECE,
        priceAtPurchase = BigDecimal(price), quantity = BigDecimal(qty),
    )

    private fun order(
        items: List<OrderItem> = listOf(orderItem("5.00", "1")),
        address: String = "123 Main St",
        type: OrderType = OrderType.ON_DEMAND,
        scheduledFor: Instant? = null,
    ) = Order(
        orderId = "ord-test", shopperId = "user-1", items = items,
        status = OrderStatus.PENDING, orderType = type,
        deliveryAddress = address, scheduledFor = scheduledFor,
        createdAt = Instant.now(),
    )
}


// ─────────────────────────────────────────────────────────────────────────────

class OrderStatusTest {
    // 15 TESTS IN THIS CLASS
    // ── displayName ───────────────────────────────────────────────────────────

    @Test
    fun `PENDING displayName is 'Pending'`() {
        assertEquals("Pending", OrderStatus.PENDING.displayName())
    }

    @Test
    fun `OUT_FOR_DELIVERY displayName replaces underscores`() {
        assertEquals("Out for delivery", OrderStatus.OUT_FOR_DELIVERY.displayName())
    }

    @Test
    fun `READY_FOR_PICKUP displayName is correct`() {
        assertEquals("Ready for pickup", OrderStatus.READY_FOR_PICKUP.displayName())
    }

    // ── isTerminal ────────────────────────────────────────────────────────────

    @Test
    fun `DELIVERED is terminal`() = assertTrue(OrderStatus.DELIVERED.isTerminal())

    @Test
    fun `CANCELLED is terminal`() = assertTrue(OrderStatus.CANCELLED.isTerminal())

    @Test
    fun `PENDING is not terminal`() = assertFalse(OrderStatus.PENDING.isTerminal())

    @Test
    fun `OUT_FOR_DELIVERY is not terminal`() =
        assertFalse(OrderStatus.OUT_FOR_DELIVERY.isTerminal())

    // ── allowedNextStatuses — happy path ─────────────────────────────────────

    @Test
    fun `PENDING can advance to CONFIRMED`() {
        assertTrue(OrderStatus.CONFIRMED in OrderStatus.PENDING.allowedNextStatuses())
    }

    @Test
    fun `PENDING can be cancelled`() {
        assertTrue(OrderStatus.CANCELLED in OrderStatus.PENDING.allowedNextStatuses())
    }

    @Test
    fun `full happy-path chain is allowed`() {
        val chain = listOf(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED,
        )
        chain.zipWithNext().forEach { (from, to) ->
            assertTrue("$from → $to should be allowed", to in from.allowedNextStatuses())
        }
    }

    // ── allowedNextStatuses — guard cases ─────────────────────────────────────

    @Test
    fun `DELIVERED has no allowed next statuses`() {
        assertTrue(OrderStatus.DELIVERED.allowedNextStatuses().isEmpty())
    }

    @Test
    fun `CANCELLED has no allowed next statuses`() {
        assertTrue(OrderStatus.CANCELLED.allowedNextStatuses().isEmpty())
    }

    @Test
    fun `PENDING cannot skip directly to PREPARING`() {
        assertFalse(OrderStatus.PREPARING in OrderStatus.PENDING.allowedNextStatuses())
    }

    @Test
    fun `CONFIRMED cannot skip to READY_FOR_PICKUP`() {
        assertFalse(OrderStatus.READY_FOR_PICKUP in OrderStatus.CONFIRMED.allowedNextStatuses())
    }

    @Test
    fun `PREPARING can be cancelled`() {
        assertTrue(OrderStatus.CANCELLED in OrderStatus.PREPARING.allowedNextStatuses())
    }
}


// ─────────────────────────────────────────────────────────────────────────────

class StatusChangeTest {
    // 2 TESTS IN THIS CLASS
    @Test(expected = IllegalArgumentException::class)
    fun `identical from and to statuses throws`() {
        StatusChange(
            fromStatus = OrderStatus.PENDING,
            toStatus = OrderStatus.PENDING,
            changedAt = Instant.now(),
            changedBy = "system",
        )
    }

    @Test
    fun `null fromStatus is valid for initial placement`() {
        val change = StatusChange(
            fromStatus = null,
            toStatus = OrderStatus.PENDING,
            changedAt = Instant.now(),
            changedBy = "system",
        )
        assertNull(change.fromStatus)
        assertEquals(OrderStatus.PENDING, change.toStatus)
    }
}