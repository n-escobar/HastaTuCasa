package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.StatusChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake deliverer-side repository.
 *
 * Shares the same [MutableStateFlow] source-of-truth as [FakeOrderRepository]
 * so that shopper-placed orders are immediately visible to the deliverer in
 * the same process (useful for demo/testing without a real backend).
 *
 * In production this would be replaced by a network-backed implementation.
 */
@Singleton
class FakeDelivererOrderRepository @Inject constructor(
    private val sharedOrderStore: SharedOrderStore,
) : DelivererOrderRepository {

    override fun observeAllOrders(): Flow<List<Order>> =
        sharedOrderStore.orders.map { list ->
            list.sortedByDescending { it.createdAt }
        }

    override fun observeActiveOrders(): Flow<List<Order>> =
        sharedOrderStore.orders.map { list ->
            list
                .filter { !it.status.isTerminal() }
                .sortedWith(
                    compareBy(
                        { statusPriority(it.status) },
                        { it.createdAt },
                    )
                )
        }

    override fun observeCompletedOrders(): Flow<List<Order>> =
        sharedOrderStore.orders.map { list ->
            list
                .filter { it.status.isTerminal() }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun getOrder(orderId: String): Order? =
        sharedOrderStore.orders.value.find { it.orderId == orderId }

    override suspend fun advanceStatus(
        orderId: String,
        newStatus: OrderStatus,
        delivererId: String,
        note: String?,
    ): Result<Order> {
        val current = sharedOrderStore.orders.value.find { it.orderId == orderId }
            ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

        if (newStatus !in current.status.allowedNextStatuses()) {
            return Result.failure(
                IllegalStateException(
                    "Cannot transition ${current.status} → $newStatus"
                )
            )
        }

        val change = StatusChange(
            fromStatus = current.status,
            toStatus = newStatus,
            changedAt = Instant.now(),
            changedBy = delivererId,
            reason = note,
        )
        val updated = current.copy(
            status = newStatus,
            delivererId = delivererId,
            statusHistory = current.statusHistory + change,
        )
        sharedOrderStore.update(updated)
        return Result.success(updated)
    }

    override suspend fun claimOrder(orderId: String, delivererId: String): Result<Order> {
        val current = sharedOrderStore.orders.value.find { it.orderId == orderId }
            ?: return Result.failure(IllegalArgumentException("Order not found: $orderId"))

        if (current.delivererId != null) {
            return Result.failure(
                IllegalStateException("Order $orderId is already claimed by ${current.delivererId}")
            )
        }

        val updated = current.copy(delivererId = delivererId)
        sharedOrderStore.update(updated)
        return Result.success(updated)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Lower value = higher priority in the active queue.
     * Orders closest to delivery appear first.
     */
    private fun statusPriority(status: OrderStatus): Int = when (status) {
        OrderStatus.OUT_FOR_DELIVERY  -> 0
        OrderStatus.READY_FOR_PICKUP  -> 1
        OrderStatus.PREPARING         -> 2
        OrderStatus.CONFIRMED         -> 3
        OrderStatus.PENDING           -> 4
        else                          -> 5
    }
}

// ─── Shared In-Memory Store ───────────────────────────────────────────────────

/**
 * Single source of truth for orders shared between the shopper-side
 * [FakeOrderRepository] and the deliverer-side [FakeDelivererOrderRepository].
 *
 * Injected as a [Singleton] so both repositories observe the same [Flow].
 */
@Singleton
class SharedOrderStore @Inject constructor() {

    val orders: MutableStateFlow<List<Order>> =
        MutableStateFlow(FakeOrderRepository.SAMPLE_ORDERS)

    fun update(order: Order) {
        orders.update { list ->
            list.map { if (it.orderId == order.orderId) order else it }
        }
    }

    fun add(order: Order) {
        orders.update { it + order }
    }
}