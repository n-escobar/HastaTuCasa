package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository used exclusively by the deliverer flavor.
 *
 * Unlike [OrderRepository] (which is scoped to a single shopper),
 * this interface exposes the full order queue so a deliverer can
 * see, claim, and advance every incoming order.
 */
interface DelivererOrderRepository {

    /** All orders across all shoppers, newest first. */
    fun observeAllOrders(): Flow<List<Order>>

    /**
     * Orders that are actionable by a deliverer:
     * PENDING, CONFIRMED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY.
     */
    fun observeActiveOrders(): Flow<List<Order>>

    /** Orders that have reached a terminal state (DELIVERED or CANCELLED). */
    fun observeCompletedOrders(): Flow<List<Order>>

    suspend fun getOrder(orderId: String): Order?

    /**
     * Advance [orderId] to [newStatus].
     * Delegates to the same underlying state machine as [OrderRepository.updateOrderStatus].
     */
    suspend fun advanceStatus(
        orderId: String,
        newStatus: OrderStatus,
        delivererId: String,
        note: String? = null,
    ): Result<Order>

    /**
     * Assign this deliverer to the order (sets [Order.delivererId]).
     * Typically called when a deliverer "claims" a CONFIRMED order.
     */
    suspend fun claimOrder(orderId: String, delivererId: String): Result<Order>
}