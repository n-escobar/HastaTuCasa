package com.example.hastatucasa.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.Product
import com.example.hastatucasa.data.model.ProductUnit
import com.example.hastatucasa.data.repository.CartRepository
import com.example.hastatucasa.data.repository.OrderRepository
import com.example.hastatucasa.data.repository.ProductRepository
import com.example.hastatucasa.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

// ─── View objects ─────────────────────────────────────────────────────────────

data class CartLineItem(
    val product: Product,
    val quantity: Int,
) {
    val subtotal: BigDecimal
        get() = product.effectivePrice
            .multiply(BigDecimal(quantity))
            .setScale(2, RoundingMode.HALF_UP)

    val unitLabel: String
        get() = if (product.unit == ProductUnit.LB) "/lb" else "/ea"
}

// ─── State ────────────────────────────────────────────────────────────────────

data class CartUiState(
    val isLoading: Boolean = true,
    val lineItems: List<CartLineItem> = emptyList(),
    val deliveryAddress: String = "",
    val isCheckingOut: Boolean = false,
    val checkoutSuccess: Boolean = false,
    val snackbarMessage: String? = null,
) {
    val subtotal: BigDecimal
        get() = lineItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotal) }
            .setScale(2, RoundingMode.HALF_UP)

    val deliveryFee: BigDecimal
        get() = if (lineItems.isEmpty()) BigDecimal.ZERO else Order.DELIVERY_FEE

    val total: BigDecimal
        get() = subtotal.add(deliveryFee).setScale(2, RoundingMode.HALF_UP)

    val isEmpty: Boolean get() = lineItems.isEmpty()

    val itemCount: Int get() = lineItems.sumOf { it.quantity }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _isCheckingOut = MutableStateFlow(false)
    private val _checkoutSuccess = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CartUiState> = combine(
        cartRepository.observeCartItems(),
        productRepository.observeProducts(),
        userRepository.observeCurrentUser(),
        combine(_isCheckingOut, _checkoutSuccess, _snackbarMessage)
        { checking, success, snackbar -> Triple(checking, success, snackbar) },
    ) { cartItems, allProducts, user, (isCheckingOut, checkoutSuccess, snackbar) ->

        // Join cart IDs → full Product objects, drop any stale IDs
        val productById = allProducts.associateBy { it.id }
        val lineItems = cartItems
            .mapNotNull { (productId, qty) ->
                productById[productId]?.let { CartLineItem(it, qty) }
            }
            .sortedBy { it.product.name }

        CartUiState(
            isLoading = false,
            lineItems = lineItems,
            deliveryAddress = user?.deliveryAddress ?: "",
            isCheckingOut = isCheckingOut,
            checkoutSuccess = checkoutSuccess,
            snackbarMessage = snackbar,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CartUiState(),
    )

    // ─── Intents ──────────────────────────────────────────────────────────────

    fun onAddItem(product: Product) {
        viewModelScope.launch { cartRepository.addItem(product) }
    }

    fun onRemoveItem(productId: String) {
        viewModelScope.launch { cartRepository.removeItem(productId) }
    }

    fun onRemoveItemCompletely(productId: String) {
        viewModelScope.launch { cartRepository.removeItemCompletely(productId) }
    }

    fun onCheckout() {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState.isEmpty) return@launch

            val user = userRepository.getCurrentUser() ?: run {
                _snackbarMessage.value = "Could not load user — please try again"
                return@launch
            }

            _isCheckingOut.value = true

            val orderItems = currentState.lineItems.map { line ->
                OrderItem(
                    productId = line.product.id,
                    productName = line.product.name,
                    productUnit = line.product.unit,
                    priceAtPurchase = line.product.effectivePrice,
                    quantity = BigDecimal(line.quantity),
                )
            }

            val order = Order(
                orderId = "ord-${UUID.randomUUID().toString().take(8)}",
                shopperId = user.id,
                items = orderItems,
                status = OrderStatus.PENDING,
                orderType = OrderType.ON_DEMAND,
                deliveryAddress = user.deliveryAddress.ifBlank { "Address not set" },
                createdAt = Instant.now(),
            )

            orderRepository.placeOrder(order)
                .onSuccess {
                    cartRepository.clearCart()
                    _checkoutSuccess.value = true
                    _snackbarMessage.value = "Order placed! 🎉"
                }
                .onFailure {
                    _snackbarMessage.value = "Checkout failed: ${it.message}"
                }

            _isCheckingOut.value = false
        }
    }

    fun onSnackbarDismissed() {
        _snackbarMessage.value = null
    }

    fun onCheckoutSuccessConsumed() {
        _checkoutSuccess.value = false
    }
}