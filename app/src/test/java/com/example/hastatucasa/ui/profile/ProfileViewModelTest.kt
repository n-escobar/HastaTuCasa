package com.example.hastatucasa.ui.profile

import app.cash.turbine.test
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.ProductUnit
import com.example.hastatucasa.data.model.User
import com.example.hastatucasa.data.model.UserRole
import com.example.hastatucasa.data.repository.OrderRepository
import com.example.hastatucasa.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    //22 TESTS IN THIS CLASS
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeUserRepo: FakeUserRepository
    private lateinit var fakeOrderRepo: FakeOrderRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeUserRepo = FakeUserRepository(SAMPLE_USER)
        fakeOrderRepo = FakeOrderRepository(SAMPLE_ORDERS)
        viewModel = ProfileViewModel(fakeUserRepo, fakeOrderRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is loading`() {
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `state contains current user after loading`() = runTest {
        viewModel.uiState.test {
            val state = awaitReadyState()
            assertEquals(SAMPLE_USER, state.user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state contains all orders after loading`() = runTest {
        viewModel.uiState.test {
            val state = awaitReadyState()
            assertEquals(SAMPLE_ORDERS.size, state.orders.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── filteredOrders extension ───────────────────────────────────────────────

    @Test
    fun `ALL filter returns every order`() {
        val state = stateWith(filter = OrderFilter.ALL)
        assertEquals(SAMPLE_ORDERS.size, state.filteredOrders.size)
    }

    @Test
    fun `ACTIVE filter returns only non-terminal orders`() {
        val state = stateWith(filter = OrderFilter.ACTIVE)
        assertTrue(state.filteredOrders.all { !it.status.isTerminal() })
        // Verify the delivered and cancelled ones are excluded
        assertFalse(state.filteredOrders.any { it.status == OrderStatus.DELIVERED })
        assertFalse(state.filteredOrders.any { it.status == OrderStatus.CANCELLED })
    }

    @Test
    fun `COMPLETED filter returns only DELIVERED orders`() {
        val state = stateWith(filter = OrderFilter.COMPLETED)
        assertTrue(state.filteredOrders.all { it.status == OrderStatus.DELIVERED })
    }

    @Test
    fun `CANCELLED filter returns only CANCELLED orders`() {
        val state = stateWith(filter = OrderFilter.CANCELLED)
        assertTrue(state.filteredOrders.all { it.status == OrderStatus.CANCELLED })
    }

    @Test
    fun `ACTIVE filter returns empty when all orders are terminal`() {
        val terminalOnly = SAMPLE_ORDERS.filter { it.status.isTerminal() }
        val state = stateWith(filter = OrderFilter.ACTIVE, orders = terminalOnly)
        assertTrue(state.filteredOrders.isEmpty())
    }

    // ── activeOrderCount extension ─────────────────────────────────────────────

    @Test
    fun `activeOrderCount counts non-terminal orders`() {
        val state = stateWith()
        val expected = SAMPLE_ORDERS.count { !it.status.isTerminal() }
        assertEquals(expected, state.activeOrderCount)
    }

    @Test
    fun `activeOrderCount is zero when all orders are terminal`() {
        val state = stateWith(orders = SAMPLE_ORDERS.filter { it.status.isTerminal() })
        assertEquals(0, state.activeOrderCount)
    }

    // ── onFilterSelected ──────────────────────────────────────────────────────

    @Test
    fun `onFilterSelected changes selectedFilter in state`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onFilterSelected(OrderFilter.ACTIVE)
            val state = awaitItem()

            assertEquals(OrderFilter.ACTIVE, state.selectedFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching filter updates filteredOrders`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onFilterSelected(OrderFilter.COMPLETED)
            val state = awaitItem()

            assertTrue(state.filteredOrders.all { it.status == OrderStatus.DELIVERED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onOrderExpandToggled ──────────────────────────────────────────────────

    @Test
    fun `toggling an order sets it as expanded`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onOrderExpandToggled("ord-001")
            val state = awaitItem()

            assertEquals("ord-001", state.expandedOrderId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling the same order again collapses it`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onOrderExpandToggled("ord-001")
            awaitItem()

            viewModel.onOrderExpandToggled("ord-001")
            val state = awaitItem()

            assertNull(state.expandedOrderId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling different order replaces expanded id`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onOrderExpandToggled("ord-001")
            awaitItem()

            viewModel.onOrderExpandToggled("ord-002")
            val state = awaitItem()

            assertEquals("ord-002", state.expandedOrderId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onCancelOrder ─────────────────────────────────────────────────────────

    @Test
    fun `cancelling a PENDING order shows success snackbar`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onCancelOrder("ord-pending", "reason")
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()

            assertNotNull(state.snackbarMessage)
            assertFalse(state.snackbarMessage!!.contains("Could not"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling a non-cancellable order shows error snackbar`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            // ord-delivered is DELIVERED, cannot cancel
            viewModel.onCancelOrder("ord-delivered", "reason")
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()

            assertTrue(state.snackbarMessage!!.startsWith("Could not"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── edit profile ──────────────────────────────────────────────────────────

    @Test
    fun `onStartEditProfile sets isEditingProfile true`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onStartEditProfile()
            val state = awaitItem()

            assertTrue(state.isEditingProfile)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCancelEditProfile clears isEditingProfile`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onStartEditProfile()
            awaitItem()

            viewModel.onCancelEditProfile()
            val state = awaitItem()

            assertFalse(state.isEditingProfile)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSaveProfile updates user and closes dialog`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onStartEditProfile()
            awaitItem() // isEditingProfile = true

            viewModel.onSaveProfile("New Name", "New Address")
            testDispatcher.scheduler.advanceUntilIdle()

            // The combine() in ProfileViewModel emits once per state change.
            // advanceUntilIdle() ensures all coroutines have completed before
            // we assert, so exactly one more emission is guaranteed.
            val finalState = awaitItem()

            assertFalse(finalState.isEditingProfile)
            assertEquals("New Name", finalState.user?.name)
            assertEquals("New Address", finalState.user?.deliveryAddress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSaveProfile shows success snackbar`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSaveProfile("Name", "Address")
            testDispatcher.scheduler.advanceUntilIdle()

            val finalState = awaitItem()

            assertEquals("Profile updated", finalState.snackbarMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onSnackbarDismissed ───────────────────────────────────────────────────

    @Test
    fun `onSnackbarDismissed clears snackbar message`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onCancelOrder("ord-pending", "reason")
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // message shown

            viewModel.onSnackbarDismissed()
            val state = awaitItem()

            assertNull(state.snackbarMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun app.cash.turbine.TurbineTestContext<ProfileUiState>.awaitReadyState(): ProfileUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    private fun stateWith(
        filter: OrderFilter = OrderFilter.ALL,
        orders: List<Order> = SAMPLE_ORDERS,
    ) = ProfileUiState(
        isLoading = false,
        user = SAMPLE_USER,
        orders = orders,
        selectedFilter = filter,
    )
}


// ─── Sample Data ──────────────────────────────────────────────────────────────

private val SAMPLE_USER = User(
    id = "user-1", name = "Alex", email = "alex@test.com",
    role = UserRole.SHOPPER, deliveryAddress = "1 Test St",
)

private fun order(
    id: String,
    shopperId: String = "user-1",
    status: OrderStatus,
) = Order(
    orderId = id, shopperId = shopperId,
    items = listOf(OrderItem("p1", "Apple", ProductUnit.PIECE, BigDecimal("1.00"), BigDecimal("1"))),
    status = status, orderType = OrderType.ON_DEMAND,
    deliveryAddress = "1 Test St", createdAt = Instant.now(),
)

private val SAMPLE_ORDERS = listOf(
    order("ord-active-1", status = OrderStatus.PENDING),
    order("ord-active-2", status = OrderStatus.OUT_FOR_DELIVERY),
    order("ord-delivered", status = OrderStatus.DELIVERED),
    order("ord-cancelled", status = OrderStatus.CANCELLED),
    order("ord-pending",   status = OrderStatus.PENDING),
)


// ─── Fake Repositories ────────────────────────────────────────────────────────

private class FakeUserRepository(initial: User) : UserRepository {
    private val _user = MutableStateFlow<User?>(initial)

    override fun observeCurrentUser(): Flow<User?> = _user

    override suspend fun getCurrentUser(): User? = _user.value

    override suspend fun updateUser(user: User): Result<User> {
        _user.value = user
        return Result.success(user)
    }
}

private class FakeOrderRepository(initial: List<Order>) : OrderRepository {
    private val _orders = MutableStateFlow(initial)

    override fun observeOrdersForShopper(shopperId: String): Flow<List<Order>> =
        _orders.map { it.filter { o -> o.shopperId == shopperId } }

    override suspend fun getOrder(orderId: String): Order? =
        _orders.value.find { it.orderId == orderId }

    override suspend fun placeOrder(order: Order): Result<Order> {
        _orders.value = _orders.value + order
        return Result.success(order)
    }

    override suspend fun cancelOrder(orderId: String, reason: String): Result<Order> =
        updateOrderStatus(orderId, OrderStatus.CANCELLED, "user", reason)

    override suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        changedBy: String,
        reason: String?,
    ): Result<Order> {
        val order = _orders.value.find { it.orderId == orderId }
            ?: return Result.failure(IllegalArgumentException("Not found: $orderId"))

        if (newStatus !in order.status.allowedNextStatuses()) {
            return Result.failure(IllegalStateException("Invalid transition: ${order.status} → $newStatus"))
        }

        val updated = order.copy(status = newStatus)
        _orders.value = _orders.value.map { if (it.orderId == orderId) updated else it }
        return Result.success(updated)
    }
}