package com.example.hastatucasa.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.User
import com.example.hastatucasa.data.repository.OrderRepository
import com.example.hastatucasa.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi


// ─── State ────────────────────────────────────────────────────────────────────

enum class OrderFilter { ALL, ACTIVE, COMPLETED, CANCELLED }

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val orders: List<Order> = emptyList(),
    val selectedFilter: OrderFilter = OrderFilter.ALL,
    val expandedOrderId: String? = null,
    val snackbarMessage: String? = null,
    val isEditingProfile: Boolean = false,
)

val ProfileUiState.filteredOrders: List<Order>
    get() = when (selectedFilter) {
        OrderFilter.ALL       -> orders
        OrderFilter.ACTIVE    -> orders.filter { !it.status.isTerminal() }
        OrderFilter.COMPLETED -> orders.filter { it.status == OrderStatus.DELIVERED }
        OrderFilter.CANCELLED -> orders.filter { it.status == OrderStatus.CANCELLED }
    }

val ProfileUiState.activeOrderCount: Int
    get() = orders.count { !it.status.isTerminal() }

// ─── ViewModel ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(OrderFilter.ALL)
    private val _expandedOrderId = MutableStateFlow<String?>(null)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _isEditingProfile = MutableStateFlow(false)

    private val orders = userRepository.observeCurrentUser()
        .filterNotNull()
        .flatMapLatest { user ->
            orderRepository.observeOrdersForShopper(user.id)
        }

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(
            userRepository.observeCurrentUser(),
            orders,
            _selectedFilter,
        ) { user, orders, filter -> Triple(user, orders, filter) },
        combine(
            _expandedOrderId,
            _snackbarMessage,
            _isEditingProfile,
        ) { expandedId, snackbar, isEditing -> Triple(expandedId, snackbar, isEditing) },
    ) { (user, orders, filter), (expandedId, snackbar, isEditing) ->
        ProfileUiState(
            isLoading = false,
            user = user,
            orders = orders,
            selectedFilter = filter,
            expandedOrderId = expandedId,
            snackbarMessage = snackbar,
            isEditingProfile = isEditing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    // ─── Intents ──────────────────────────────────────────────────────────────

    fun onFilterSelected(filter: OrderFilter) {
        _selectedFilter.value = filter
    }

    fun onOrderExpandToggled(orderId: String) {
        _expandedOrderId.update { current ->
            if (current == orderId) null else orderId
        }
    }

    fun onCancelOrder(orderId: String, reason: String = "Cancelled by shopper") {
        viewModelScope.launch {
            orderRepository.cancelOrder(orderId, reason)
                .onSuccess { _snackbarMessage.value = "Order #${orderId.takeLast(4)} cancelled" }
                .onFailure { _snackbarMessage.value = "Could not cancel order: ${it.message}" }
        }
    }

    fun onStartEditProfile() {
        _isEditingProfile.value = true
    }

    fun onSaveProfile(name: String, address: String) {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser() ?: return@launch
            val updated = user.copy(name = name.trim(), deliveryAddress = address.trim())
            userRepository.updateUser(updated)
                .onSuccess {
                    _isEditingProfile.value = false
                    _snackbarMessage.value = "Profile updated"
                }
                .onFailure { _snackbarMessage.value = "Failed to save profile" }
        }
    }

    fun onCancelEditProfile() {
        _isEditingProfile.value = false
    }

    fun onSnackbarDismissed() {
        _snackbarMessage.value = null
    }
}