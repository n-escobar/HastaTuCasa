package com.example.hastatucasa.ui.deliverer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.repository.DelivererOrderRepository
import com.example.hastatucasa.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Tab ──────────────────────────────────────────────────────────────────────

enum class DelivererTab { ACTIVE, HISTORY }

// ─── State ────────────────────────────────────────────────────────────────────

data class DelivererOrdersUiState(
    val isLoading: Boolean = true,
    val delivererId: String = "",
    val activeOrders: List<Order> = emptyList(),
    val completedOrders: List<Order> = emptyList(),
    val selectedTab: DelivererTab = DelivererTab.ACTIVE,
    val expandedOrderId: String? = null,
    val pendingActionOrderId: String? = null,   // shows progress on that card
    val snackbarMessage: String? = null,
)

val DelivererOrdersUiState.displayedOrders: List<Order>
    get() = if (selectedTab == DelivererTab.ACTIVE) activeOrders else completedOrders

/** Label for the primary action button on an active order card. */
fun Order.nextActionLabel(): String? = when (status) {
    OrderStatus.PENDING          -> "Confirm"
    OrderStatus.CONFIRMED        -> "Start Preparing"
    OrderStatus.PREPARING        -> "Ready for Pickup"
    OrderStatus.READY_FOR_PICKUP -> "Pick Up & Deliver"
    OrderStatus.OUT_FOR_DELIVERY -> "Mark Delivered"
    else                         -> null
}

fun Order.nextStatus(): OrderStatus? = when (status) {
    OrderStatus.PENDING          -> OrderStatus.CONFIRMED
    OrderStatus.CONFIRMED        -> OrderStatus.PREPARING
    OrderStatus.PREPARING        -> OrderStatus.READY_FOR_PICKUP
    OrderStatus.READY_FOR_PICKUP -> OrderStatus.OUT_FOR_DELIVERY
    OrderStatus.OUT_FOR_DELIVERY -> OrderStatus.DELIVERED
    else                         -> null
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DelivererOrdersViewModel @Inject constructor(
    private val delivererOrderRepository: DelivererOrderRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(DelivererTab.ACTIVE)
    private val _expandedOrderId = MutableStateFlow<String?>(null)
    private val _pendingActionOrderId = MutableStateFlow<String?>(null)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DelivererOrdersUiState> = combine(
        userRepository.observeCurrentUser(),
        delivererOrderRepository.observeActiveOrders(),
        delivererOrderRepository.observeCompletedOrders(),
        combine(
            _selectedTab,
            _expandedOrderId,
            _pendingActionOrderId,
            _snackbarMessage,
        ) { tab, expanded, pending, snackbar ->
            UiMeta(tab, expanded, pending, snackbar)
        },
    ) { user, active, completed, meta ->
        DelivererOrdersUiState(
            isLoading = false,
            delivererId = user?.id ?: "deliverer-1",
            activeOrders = active,
            completedOrders = completed,
            selectedTab = meta.tab,
            expandedOrderId = meta.expandedOrderId,
            pendingActionOrderId = meta.pendingActionOrderId,
            snackbarMessage = meta.snackbarMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DelivererOrdersUiState(),
    )

    // ─── Intents ──────────────────────────────────────────────────────────────

    fun onTabSelected(tab: DelivererTab) {
        _selectedTab.value = tab
        _expandedOrderId.value = null
    }

    fun onOrderExpandToggled(orderId: String) {
        _expandedOrderId.update { current ->
            if (current == orderId) null else orderId
        }
    }

    /**
     * Advance the order to its next logical status.
     * Also claims the order for this deliverer if not yet claimed.
     */
    fun onAdvanceStatus(order: Order) {
        val nextStatus = order.nextStatus() ?: return
        val delivererId = uiState.value.delivererId

        viewModelScope.launch {
            _pendingActionOrderId.value = order.orderId

            // Claim if this deliverer isn't already assigned
            if (order.delivererId == null) {
                delivererOrderRepository.claimOrder(order.orderId, delivererId)
            }

            delivererOrderRepository.advanceStatus(
                orderId = order.orderId,
                newStatus = nextStatus,
                delivererId = delivererId,
            ).onSuccess {
                _snackbarMessage.value =
                    "Order #${order.orderId.takeLast(5).uppercase()} → ${nextStatus.displayName()}"
            }.onFailure {
                _snackbarMessage.value = "Failed: ${it.message}"
            }

            _pendingActionOrderId.value = null
        }
    }

    fun onSnackbarDismissed() {
        _snackbarMessage.value = null
    }
}

// ─── Private helpers ──────────────────────────────────────────────────────────

private data class UiMeta(
    val tab: DelivererTab,
    val expandedOrderId: String?,
    val pendingActionOrderId: String?,
    val snackbarMessage: String?,
)