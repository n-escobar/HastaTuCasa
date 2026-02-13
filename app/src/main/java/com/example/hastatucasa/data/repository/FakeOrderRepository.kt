package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.ProductUnit
import com.example.hastatucasa.data.model.StatusChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeOrderRepository @Inject constructor() : OrderRepository {

    private val orders = MutableStateFlow(SAMPLE_ORDERS)

    override fun observeOrdersForShopper(shopperId: String): Flow<List<Order>> =
        orders.map { list ->
            list.filter { it.shopperId == shopperId }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun getOrder(orderId: String): Order? =
        orders.value.find { it.orderId == orderId }

    override suspend fun placeOrder(order: Order): Result<Order> {
        orders.update { current -> current + order }
        return Result.success(order)
    }

    override suspend fun cancelOrder(orderId: String, reason: String): Result<Order> =
        updateOrderStatus(orderId, OrderStatus.CANCELLED, "shopper", reason)

    override suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        changedBy: String,
        reason: String?
    ): Result<Order> {
        val current = orders.value.find { it.orderId == orderId }
            ?: return Result.failure(IllegalArgumentException("Order $orderId not found"))

        if (newStatus !in current.status.allowedNextStatuses()) {
            return Result.failure(
                IllegalStateException(
                    "Cannot transition from ${current.status} to $newStatus"
                )
            )
        }

        val change = StatusChange(
            fromStatus = current.status,
            toStatus = newStatus,
            changedAt = Instant.now(),
            changedBy = changedBy,
            reason = reason
        )
        val updated = current.copy(
            status = newStatus,
            statusHistory = current.statusHistory + change
        )
        orders.update { list -> list.map { if (it.orderId == orderId) updated else it } }
        return Result.success(updated)
    }

    companion object {
        private val now: Instant = Instant.now()

        val SAMPLE_ORDERS = listOf(
            Order(
                orderId = "ord-001",
                shopperId = "user-shopper",
                delivererId = "del-001",
                items = listOf(
                    OrderItem("p1", "Organic Fuji Apples", ProductUnit.LB,  BigDecimal("3.59"),  BigDecimal("2")),
                    OrderItem("d1", "Whole Milk 1gal",     ProductUnit.PIECE, BigDecimal("5.49"), BigDecimal("1")),
                    OrderItem("b1", "Sourdough Loaf",      ProductUnit.PIECE, BigDecimal("5.99"), BigDecimal("1")),
                ),
                status = OrderStatus.OUT_FOR_DELIVERY,
                orderType = OrderType.ON_DEMAND,
                deliveryAddress = "123 Main St, Springfield",
                createdAt = now.minus(2, ChronoUnit.HOURS),
                statusHistory = listOf(
                    StatusChange(null,                        OrderStatus.PENDING,          now.minus(2, ChronoUnit.HOURS),   "system"),
                    StatusChange(OrderStatus.PENDING,         OrderStatus.CONFIRMED,        now.minus(110, ChronoUnit.MINUTES), "system"),
                    StatusChange(OrderStatus.CONFIRMED,       OrderStatus.PREPARING,        now.minus(90,  ChronoUnit.MINUTES), "del-001"),
                    StatusChange(OrderStatus.PREPARING,       OrderStatus.READY_FOR_PICKUP, now.minus(30,  ChronoUnit.MINUTES), "del-001"),
                    StatusChange(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY, now.minus(10, ChronoUnit.MINUTES), "del-001"),
                )
            ),
            Order(
                orderId = "ord-002",
                shopperId = "user-shopper",
                items = listOf(
                    OrderItem("m1", "Chicken Breast",        ProductUnit.LB,    BigDecimal("6.99"), BigDecimal("1.5")),
                    OrderItem("n2", "Olive Oil Extra Virgin", ProductUnit.PIECE, BigDecimal("10.99"), BigDecimal("1")),
                    OrderItem("p5", "Roma Tomatoes",         ProductUnit.LB,    BigDecimal("2.49"), BigDecimal("1")),
                    OrderItem("n1", "Pasta Spaghetti 1lb",   ProductUnit.PIECE, BigDecimal("2.29"), BigDecimal("2")),
                ),
                status = OrderStatus.PENDING,
                orderType = OrderType.SCHEDULED,
                deliveryAddress = "123 Main St, Springfield",
                scheduledFor = now.plus(2, ChronoUnit.DAYS),
                createdAt = now.minus(30, ChronoUnit.MINUTES),
                statusHistory = listOf(
                    StatusChange(null, OrderStatus.PENDING, now.minus(30, ChronoUnit.MINUTES), "system"),
                )
            ),
            Order(
                orderId = "ord-003",
                shopperId = "user-shopper",
                delivererId = "del-002",
                items = listOf(
                    OrderItem("v1", "Orange Juice 52oz",   ProductUnit.PIECE, BigDecimal("5.99"), BigDecimal("2")),
                    OrderItem("d4", "Large Eggs 12ct",     ProductUnit.PIECE, BigDecimal("4.79"), BigDecimal("1")),
                    OrderItem("b2", "Blueberry Muffins 4pk", ProductUnit.PIECE, BigDecimal("3.99"), BigDecimal("1")),
                ),
                status = OrderStatus.DELIVERED,
                orderType = OrderType.ON_DEMAND,
                deliveryAddress = "123 Main St, Springfield",
                createdAt = now.minus(3, ChronoUnit.DAYS),
                statusHistory = listOf(
                    StatusChange(null,                          OrderStatus.PENDING,           now.minus(3, ChronoUnit.DAYS),                   "system"),
                    StatusChange(OrderStatus.PENDING,           OrderStatus.CONFIRMED,         now.minus(3, ChronoUnit.DAYS).plus(5,  ChronoUnit.MINUTES), "system"),
                    StatusChange(OrderStatus.CONFIRMED,         OrderStatus.PREPARING,         now.minus(3, ChronoUnit.DAYS).plus(15, ChronoUnit.MINUTES), "del-002"),
                    StatusChange(OrderStatus.PREPARING,         OrderStatus.READY_FOR_PICKUP,  now.minus(3, ChronoUnit.DAYS).plus(40, ChronoUnit.MINUTES), "del-002"),
                    StatusChange(OrderStatus.READY_FOR_PICKUP,  OrderStatus.OUT_FOR_DELIVERY,  now.minus(3, ChronoUnit.DAYS).plus(50, ChronoUnit.MINUTES), "del-002"),
                    StatusChange(OrderStatus.OUT_FOR_DELIVERY,  OrderStatus.DELIVERED,         now.minus(3, ChronoUnit.DAYS).plus(75, ChronoUnit.MINUTES), "del-002"),
                )
            ),
            Order(
                orderId = "ord-004",
                shopperId = "user-shopper",
                items = listOf(
                    OrderItem("d2", "Greek Yogurt Plain",  ProductUnit.PIECE, BigDecimal("5.94"), BigDecimal("2")),
                    OrderItem("n4", "Rolled Oats 42oz",    ProductUnit.PIECE, BigDecimal("6.59"), BigDecimal("1")),
                ),
                status = OrderStatus.CANCELLED,
                orderType = OrderType.ON_DEMAND,
                deliveryAddress = "123 Main St, Springfield",
                createdAt = now.minus(5, ChronoUnit.DAYS),
                statusHistory = listOf(
                    StatusChange(null,             OrderStatus.PENDING,   now.minus(5, ChronoUnit.DAYS),                   "system"),
                    StatusChange(OrderStatus.PENDING, OrderStatus.CANCELLED, now.minus(5, ChronoUnit.DAYS).plus(3, ChronoUnit.MINUTES), "user-shopper", "Changed my mind"),
                )
            ),
        )
    }
}