package com.example.hastatucasa.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

// ─── Enums ────────────────────────────────────────────────────────────────────

enum class UserRole { SHOPPER, DELIVERER }

enum class ProductUnit {
    PIECE,  // countable   – eggs, bottles
    LB,     // uncountable – fruit, meat
}

enum class OrderStatus {
    PENDING, CONFIRMED, PREPARING,
    READY_FOR_PICKUP, OUT_FOR_DELIVERY,
    DELIVERED, CANCELLED;

    fun displayName(): String = name.replace('_', ' ').lowercase()
        .replaceFirstChar { it.uppercaseChar() }

    fun isTerminal(): Boolean = this == DELIVERED || this == CANCELLED

    fun allowedNextStatuses(): Set<OrderStatus> = when (this) {
        PENDING           -> setOf(CONFIRMED, CANCELLED)
        CONFIRMED         -> setOf(PREPARING, CANCELLED)
        PREPARING         -> setOf(READY_FOR_PICKUP, CANCELLED)
        READY_FOR_PICKUP  -> setOf(OUT_FOR_DELIVERY, CANCELLED)
        OUT_FOR_DELIVERY  -> setOf(DELIVERED, CANCELLED)
        DELIVERED         -> emptySet()
        CANCELLED         -> emptySet()
    }
}

enum class OrderType { ON_DEMAND, SCHEDULED }

// ─── Domain Models ────────────────────────────────────────────────────────────

data class Product(
    val id: String,
    val name: String,
    val price: BigDecimal,
    val imageUrl: String,
    val category: String,
    val unit: ProductUnit = ProductUnit.PIECE,
    val discountPercent: BigDecimal = BigDecimal.ZERO
) {
    init {
        require(price >= BigDecimal.ZERO) { "Price cannot be negative: $price" }
        require(discountPercent >= BigDecimal.ZERO && discountPercent <= BigDecimal("100")) {
            "Discount must be between 0 and 100: $discountPercent"
        }
    }

    val effectivePrice: BigDecimal
        get() = price.multiply(
            BigDecimal.ONE.subtract(
                discountPercent.divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)
            )
        ).setScale(2, RoundingMode.HALF_UP)

    val hasDiscount: Boolean get() = discountPercent > BigDecimal.ZERO
}

data class OrderItem(
    val productId: String,
    val productName: String,
    val productUnit: ProductUnit,
    val priceAtPurchase: BigDecimal,
    val quantity: BigDecimal
) {
    init {
        require(priceAtPurchase >= BigDecimal.ZERO) { "Price at purchase cannot be negative" }
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive: $quantity" }
        if (productUnit == ProductUnit.PIECE) {
            require(quantity.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                "Quantity for PIECE products must be a whole number"
            }
        }
    }

    val subtotal: BigDecimal
        get() = priceAtPurchase
            .multiply(quantity)
            .setScale(2, RoundingMode.HALF_UP)
}

data class StatusChange(
    val fromStatus: OrderStatus?,
    val toStatus: OrderStatus,
    val changedAt: Instant,
    val changedBy: String,
    val reason: String? = null
) {
    init {
        require(fromStatus != toStatus) { "Status change must represent an actual transition" }
    }
}

data class Order(
    val orderId: String,
    val shopperId: String,
    val delivererId: String? = null,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val orderType: OrderType,
    val deliveryAddress: String,
    val scheduledFor: Instant? = null,
    val createdAt: Instant,
    val statusHistory: List<StatusChange> = emptyList()
) {
    init {
        require(items.isNotEmpty()) { "Order must contain items" }
        require(deliveryAddress.isNotBlank()) { "Delivery address cannot be blank" }
        require((orderType == OrderType.SCHEDULED) == (scheduledFor != null)) {
            "Scheduled orders must have a scheduledFor time; on-demand orders must not"
        }
    }

    val totalPrice: BigDecimal
        get() = items
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotal) }
            .setScale(2, RoundingMode.HALF_UP)
}

// ─── User ─────────────────────────────────────────────────────────────────────

data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val avatarUrl: String? = null,
    val deliveryAddress: String = ""
)