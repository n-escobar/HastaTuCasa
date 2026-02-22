package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.ProductUnit
import com.example.hastatucasa.data.model.StatusChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [OrderRepository] backed by Cloud Firestore.
 *
 * Firestore schema
 * ─────────────────
 * orders/{orderId}
 *   orderId        : String
 *   shopperId      : String
 *   delivererId    : String?
 *   status         : String          ← OrderStatus.name
 *   orderType      : String          ← OrderType.name
 *   deliveryAddress: String
 *   scheduledFor   : Long?           ← epoch millis
 *   createdAt      : Long            ← epoch millis
 *   totalPrice     : String          ← BigDecimal serialised as String
 *   items          : List<Map>       ← see [orderItemToMap]
 *   statusHistory  : List<Map>       ← see [statusChangeToMap]
 */
@Singleton
class FirebaseOrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : OrderRepository {

    private val ordersCol = firestore.collection("orders")

    // ── observe ───────────────────────────────────────────────────────────────

    override fun observeOrdersForShopper(shopperId: String): Flow<List<Order>> =
        ordersCol
            .whereEqualTo("shopperId", shopperId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toOrder() } }

    // ── read ──────────────────────────────────────────────────────────────────

    override suspend fun getOrder(orderId: String): Order? =
        ordersCol.document(orderId).get().await().toOrder()

    // ── write ─────────────────────────────────────────────────────────────────

    override suspend fun placeOrder(order: Order): Result<Order> = runCatching {
        ordersCol.document(order.orderId).set(order.toMap()).await()
        order
    }

    override suspend fun cancelOrder(orderId: String, reason: String): Result<Order> =
        updateOrderStatus(orderId, OrderStatus.CANCELLED, "shopper", reason)

    override suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        changedBy: String,
        reason: String?,
    ): Result<Order> = runCatching {
        val docRef = ordersCol.document(orderId)

        firestore.runTransaction { tx ->
            val snap = tx.get(docRef)
            val current = snap.toOrder()
                ?: error("Order $orderId not found")

            check(newStatus in current.status.allowedNextStatuses()) {
                "Cannot transition ${current.status} → $newStatus"
            }

            val change = StatusChange(
                fromStatus = current.status,
                toStatus = newStatus,
                changedAt = Instant.now(),
                changedBy = changedBy,
                reason = reason,
            )
            val updated = current.copy(
                status = newStatus,
                statusHistory = current.statusHistory + change,
            )

            tx.set(docRef, updated.toMap())
            updated
        }.await()
    }

    // ── serialisation ─────────────────────────────────────────────────────────

    private fun Order.toMap(): Map<String, Any?> = mapOf(
        "orderId"         to orderId,
        "shopperId"       to shopperId,
        "delivererId"     to delivererId,
        "status"          to status.name,
        "orderType"       to orderType.name,
        "deliveryAddress" to deliveryAddress,
        "scheduledFor"    to scheduledFor?.toEpochMilli(),
        "createdAt"       to createdAt.toEpochMilli(),
        "items"           to items.map { it.toMap() },
        "statusHistory"   to statusHistory.map { it.toMap() },
    )

    private fun OrderItem.toMap(): Map<String, Any?> = mapOf(
        "productId"       to productId,
        "productName"     to productName,
        "productUnit"     to productUnit.name,
        "priceAtPurchase" to priceAtPurchase.toPlainString(),
        "quantity"        to quantity.toPlainString(),
    )

    private fun StatusChange.toMap(): Map<String, Any?> = mapOf(
        "fromStatus" to fromStatus?.name,
        "toStatus"   to toStatus.name,
        "changedAt"  to changedAt.toEpochMilli(),
        "changedBy"  to changedBy,
        "reason"     to reason,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toOrder(): Order? {
        if (!exists()) return null
        return try {
            val rawItems = get("items") as? List<*> ?: emptyList<Any>()
            val items = rawItems.mapNotNull { it.toOrderItem() }

            val rawHistory = get("statusHistory") as? List<*> ?: emptyList<Any>()
            val history = rawHistory.mapNotNull { it.toStatusChange() }

            Order(
                orderId         = getString("orderId") ?: id,
                shopperId       = getString("shopperId") ?: return null,
                delivererId     = getString("delivererId"),
                status          = OrderStatus.valueOf(getString("status") ?: return null),
                orderType       = OrderType.valueOf(getString("orderType") ?: return null),
                deliveryAddress = getString("deliveryAddress") ?: "",
                scheduledFor    = getLong("scheduledFor")?.let { Instant.ofEpochMilli(it) },
                createdAt       = Instant.ofEpochMilli(getLong("createdAt") ?: return null),
                items           = items,
                statusHistory   = history,
            )
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.toOrderItem(): OrderItem? {
        val m = this as? Map<String, Any?> ?: return null
        return try {
            OrderItem(
                productId       = m["productId"] as? String ?: return null,
                productName     = m["productName"] as? String ?: return null,
                productUnit     = ProductUnit.valueOf(m["productUnit"] as? String ?: return null),
                priceAtPurchase = BigDecimal(m["priceAtPurchase"] as? String ?: return null),
                quantity        = BigDecimal(m["quantity"] as? String ?: return null),
            )
        } catch (e: Exception) { null }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.toStatusChange(): StatusChange? {
        val m = this as? Map<String, Any?> ?: return null
        return try {
            StatusChange(
                fromStatus = (m["fromStatus"] as? String)?.let { OrderStatus.valueOf(it) },
                toStatus   = OrderStatus.valueOf(m["toStatus"] as? String ?: return null),
                changedAt  = Instant.ofEpochMilli(
                    (m["changedAt"] as? Number)?.toLong() ?: return null
                ),
                changedBy  = m["changedBy"] as? String ?: return null,
                reason     = m["reason"] as? String,
            )
        } catch (e: Exception) { null }
    }
}