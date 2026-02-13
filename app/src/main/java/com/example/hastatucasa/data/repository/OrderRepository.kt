package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderStatus
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeOrdersForShopper(shopperId: String): Flow<List<Order>>
    suspend fun getOrder(orderId: String): Order?
    suspend fun placeOrder(order: Order): Result<Order>
    suspend fun cancelOrder(orderId: String, reason: String): Result<Order>
    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        changedBy: String,
        reason: String? = null
    ): Result<Order>
}