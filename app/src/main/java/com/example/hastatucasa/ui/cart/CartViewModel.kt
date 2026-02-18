package com.example.hastatucasa.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hastatucasa.data.model.DeliverySlot
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.Product
import com.example.hastatucasa.data.model.ProductUnit
import com.example.hastatucasa.data.repository.CartRepository
import com.example.hastatucasa.data.repository.OrderRepository
import com.example.hastatucasa.data.repository.ProductRepository
import com.example.hastatucasa.data.repository.SlotRepository
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
import java.time.LocalDate
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
    // ── Scheduling ────────────────────────────────────────────────────────────
    val availableSlots: List<DeliverySlot> = emptyList(),
    val selectedSlot: DeliverySlot? = null,
    val isSlotPickerOpen: Boolean = false,
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
    private val slotRepository: SlotRepository,
) : ViewModel() {

    private val _isCheckingOut = MutableStateFlow(false)
    private val _checkoutSuccess = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _selectedSlot = MutableStateFlow<DeliverySlot?>(null)
    private val _isSlotPickerOpen = MutableStateFlow(false)

    val uiState: StateFlow<CartUiState> = combine(
        // Stream 1: cart items joined with product catalogue
        combine(
            cartRepository.observeCartItems(),
            productRepository.observeProducts(),
        ) { cartItems, allProducts ->
            val productById = allProducts.associateBy { it.id }
            cartItems
                .mapNotNull { (productId, qty) ->
                    productById[productId]?.let { CartLineItem(it, qty) }
                }
                .sortedBy { it.product.name }
        },
        // Stream 2: current user
        userRepository.observeCurrentUser(),
        // Stream 3: available slots for the next 7 days
        slotRepository.observeAvailableSlots(LocalDate.now()),
        // Stream 4: transient UI state
        combine(
            _isCheckingOut,
            _checkoutSuccess,
            _snackbarMessage,
            _selectedSlot,
            _isSlotPickerOpen,
        ) { checking, success, snackbar, slot, pickerOpen ->
            CheckoutMeta(checking, success, snackbar, slot, pickerOpen)
        },
    ) { lineItems, user, slots, meta ->
        CartUiState(
            isLoading = false,
            lineItems = lineItems,
            deliveryAddress = user?.deliveryAddress ?: "",
            isCheckingOut = meta.isCheckingOut,
            checkoutSuccess = meta.checkoutSuccess,
            snackbarMessage = meta.snackbarMessage,
            availableSlots = slots,
            selectedSlot = meta.selectedSlot,
            isSlotPickerOpen = meta.isSlotPickerOpen,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CartUiState(),
    )

    // ─── Cart intents ─────────────────────────────────────────────────────────

    fun onAddItem(product: Product) {
        viewModelScope.launch { cartRepository.addItem(product) }
    }

    fun onRemoveItem(productId: String) {
        viewModelScope.launch { cartRepository.removeItem(productId) }
    }

    fun onRemoveItemCompletely(productId: String) {
        viewModelScope.launch { cartRepository.removeItemCompletely(productId) }
    }

    // ─── Delivery mode intents ────────────────────────────────────────────────

    /** Shopper tapped "Deliver Now" — place an on-demand order immediately. */
    fun onDeliverNow() {
        viewModelScope.launch {
            placeOrder(type = OrderType.ON_DEMAND, scheduledFor = null)
        }
    }

    /** Shopper tapped "Schedule Delivery" — open the slot picker. */
    fun onScheduleDelivery() {
        _isSlotPickerOpen.value = true
    }

    fun onSlotSelected(slot: DeliverySlot) {
        _selectedSlot.value = slot
        _isSlotPickerOpen.value = false
    }

    fun onSlotPickerDismissed() {
        _isSlotPickerOpen.value = false
    }

    fun onChangeSlot() {
        _isSlotPickerOpen.value = true
    }

    /**
     * Shopper confirmed a scheduled order.
     * No-op if no slot has been selected (defensive guard; UI should prevent this).
     */
    fun onConfirmScheduled() {
        val slot = _selectedSlot.value ?: return
        viewModelScope.launch {
            slotRepository.bookSlot(slot.id)    // soft limit — result is informational only
            placeOrder(
                type = OrderType.SCHEDULED,
                scheduledFor = slot.startTime(),
            )
        }
    }

    // ─── Snackbar / event intents ─────────────────────────────────────────────

    fun onSnackbarDismissed() {
        _snackbarMessage.value = null
    }

    fun onCheckoutSuccessConsumed() {
        _checkoutSuccess.value = false
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun placeOrder(type: OrderType, scheduledFor: Instant?) {
        val currentState = uiState.value
        if (currentState.isEmpty) return

        val user = userRepository.getCurrentUser() ?: run {
            _snackbarMessage.value = "Could not load user — please try again"
            return
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
            orderType = type,
            deliveryAddress = user.deliveryAddress.ifBlank { "Address not set" },
            scheduledFor = scheduledFor,
            createdAt = Instant.now(),
        )

        orderRepository.placeOrder(order)
            .onSuccess {
                cartRepository.clearCart()
                _selectedSlot.value = null
                _checkoutSuccess.value = true
                _snackbarMessage.value = "Order placed! 🎉"
            }
            .onFailure {
                _snackbarMessage.value = "Checkout failed: ${it.message}"
            }

        _isCheckingOut.value = false
    }
}

// ─── Private helpers ──────────────────────────────────────────────────────────

private data class CheckoutMeta(
    val isCheckingOut: Boolean,
    val checkoutSuccess: Boolean,
    val snackbarMessage: String?,
    val selectedSlot: DeliverySlot?,
    val isSlotPickerOpen: Boolean,
)