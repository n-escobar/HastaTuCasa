package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [DelivererOrderRepository] backed by Cloud Firestore.
 *
 * Reads from the same "orders" collection as [FirebaseOrderRepository].
 * The deliverer sees ALL orders (no shopperId filter).
 *
 * Active orders are filtered client-side after the snapshot arrives so that
 * a single collection-level listener is reused across both queries.
 *
 * For large deployments, move the `isTerminal` filter to a Firestore
 * composite index on `status` (add it to your firestore.indexes.json).
 */
@Singleton
class FirebaseDelivererOrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    // Re-use the same repo instance to avoid duplicating serialisation logic.
    private val orderRepository: FirebaseOrderRepository,
) : DelivererOrderRepository {

    private val ordersCol = firestore.collection("orders")

    // ── observe ───────────────────────────────────────────────────────────────

    override fun observeAllOrders(): Flow<List<Order>> =
        ordersCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull { it.toOrderViaRepo() } }

    override fun observeActiveOrders(): Flow<List<Order>> =
        observeAllOrders().map { orders ->
            orders
                .filter { !it.status.isTerminal() }
                .sortedWith(compareBy({ statusPriority(it.status) }, { it.createdAt }))
        }

    override fun observeCompletedOrders(): Flow<List<Order>> =
        observeAllOrders().map { orders ->
            orders.filter { it.status.isTerminal() }
                .sortedByDescending { it.createdAt }
        }

    // ── read ──────────────────────────────────────────────────────────────────

    override suspend fun getOrder(orderId: String): Order? =
        orderRepository.getOrder(orderId)

    // ── write ─────────────────────────────────────────────────────────────────

    override suspend fun advanceStatus(
        orderId: String,
        newStatus: OrderStatus,
        delivererId: String,
        note: String?,
    ): Result<Order> {
        // First stamp the delivererId onto the document if not yet claimed,
        // then delegate the atomic read-validate-write to updateOrderStatus,
        // which runs its own Firestore transaction.
        if (orderRepository.getOrder(orderId)?.delivererId == null) {
            claimOrder(orderId, delivererId)
        }
        return orderRepository.updateOrderStatus(orderId, newStatus, delivererId, note)
    }

    override suspend fun claimOrder(orderId: String, delivererId: String): Result<Order> =
        runCatching {
            val docRef = ordersCol.document(orderId)

            firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                val existingDelivererId = snap.getString("delivererId")
                check(existingDelivererId == null) {
                    "Order $orderId already claimed by $existingDelivererId"
                }
                tx.update(docRef, "delivererId", delivererId)
            }.await()

            orderRepository.getOrder(orderId) ?: error("Order $orderId not found after claim")
        }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Bridge to the deserialisation logic in [FirebaseOrderRepository].
     * We call [FirebaseOrderRepository.getOrder] per document to avoid
     * duplicating the mapping code; swap to a shared mapper utility if
     * performance becomes a concern.
     */
    private suspend fun com.google.firebase.firestore.DocumentSnapshot.toOrderViaRepo(): Order? =
        orderRepository.getOrder(id)

    private fun statusPriority(status: OrderStatus): Int = when (status) {
        OrderStatus.OUT_FOR_DELIVERY -> 0
        OrderStatus.READY_FOR_PICKUP -> 1
        OrderStatus.PREPARING        -> 2
        OrderStatus.CONFIRMED        -> 3
        OrderStatus.PENDING          -> 4
        else                         -> 5
    }
}