package com.example.hastatucasa.ui.cart

import app.cash.turbine.test
import com.example.hastatucasa.data.model.DeliverySlot
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.Product
import com.example.hastatucasa.data.model.ProductUnit
import com.example.hastatucasa.data.model.SlotType
import com.example.hastatucasa.data.model.User
import com.example.hastatucasa.data.model.UserRole
import com.example.hastatucasa.data.repository.CartRepository
import com.example.hastatucasa.data.repository.OrderRepository
import com.example.hastatucasa.data.repository.ProductRepository
import com.example.hastatucasa.data.repository.SlotRepository
import com.example.hastatucasa.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelScheduledTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeCartRepo: FakeCartRepository
    private lateinit var fakeProductRepo: FakeProductRepository
    private lateinit var fakeOrderRepo: FakeOrderRepository
    private lateinit var fakeUserRepo: FakeUserRepository
    private lateinit var fakeSlotRepo: FakeSlotRepository
    private lateinit var viewModel: CartViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeCartRepo = FakeCartRepository()
        fakeProductRepo = FakeProductRepository()
        fakeOrderRepo = FakeOrderRepository()
        fakeUserRepo = FakeUserRepository()
        fakeSlotRepo = FakeSlotRepository()
        viewModel = CartViewModel(
            fakeCartRepo, fakeProductRepo, fakeOrderRepo, fakeUserRepo, fakeSlotRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── slot picker open/close ─────────────────────────────────────────────────

    @Test
    fun `onScheduleDelivery opens the slot picker`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onScheduleDelivery()
            val state = awaitItem()

            assertTrue(state.isSlotPickerOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSlotPickerDismissed closes picker without selecting a slot`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onScheduleDelivery()
            awaitItem()

            viewModel.onSlotPickerDismissed()
            val state = awaitItem()

            assertFalse(state.isSlotPickerOpen)
            assertNull(state.selectedSlot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSlotSelected stores the slot and closes the picker`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onScheduleDelivery()
            awaitItem()

            viewModel.onSlotSelected(SAMPLE_SLOT)
            val state = awaitItem()

            assertFalse(state.isSlotPickerOpen)
            assertEquals(SAMPLE_SLOT, state.selectedSlot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChangeSlot reopens picker while keeping the current slot`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSlotSelected(SAMPLE_SLOT)
            awaitItem()

            viewModel.onChangeSlot()
            val state = awaitItem()

            assertTrue(state.isSlotPickerOpen)
            assertEquals(SAMPLE_SLOT, state.selectedSlot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── available slots in state ───────────────────────────────────────────────

    @Test
    fun `available slots are populated from the repository`() = runTest {
        viewModel.uiState.test {
            val state = awaitReadyState()
            assertFalse(state.availableSlots.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onDeliverNow ──────────────────────────────────────────────────────────

    @Test
    fun `onDeliverNow places an ON_DEMAND order`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onDeliverNow()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // checkoutSuccess = true

            val placed = fakeOrderRepo.lastPlacedOrder
            assertNotNull(placed)
            assertEquals(OrderType.ON_DEMAND, placed!!.orderType)
            assertNull(placed.scheduledFor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeliverNow sets checkoutSuccess to true on success`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onDeliverNow()
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()

            assertTrue(state.checkoutSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeliverNow clears the cart after success`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onDeliverNow()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // checkoutSuccess

            assertTrue(fakeCartRepo.getCartItemsSnapshot().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onConfirmScheduled ────────────────────────────────────────────────────

    @Test
    fun `onConfirmScheduled places a SCHEDULED order`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSlotSelected(SAMPLE_SLOT)
            awaitItem()

            viewModel.onConfirmScheduled()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // checkoutSuccess

            val placed = fakeOrderRepo.lastPlacedOrder
            assertNotNull(placed)
            assertEquals(OrderType.SCHEDULED, placed!!.orderType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmScheduled sets scheduledFor to slot startTime`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSlotSelected(SAMPLE_SLOT)
            awaitItem()

            viewModel.onConfirmScheduled()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()

            val placed = fakeOrderRepo.lastPlacedOrder
            assertEquals(SAMPLE_SLOT.startTime(), placed?.scheduledFor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmScheduled books the slot in the repository`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)
        fakeSlotRepo.seedSlot(SAMPLE_SLOT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSlotSelected(SAMPLE_SLOT)
            awaitItem()

            viewModel.onConfirmScheduled()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()

            assertEquals(1, fakeSlotRepo.bookCallCount(SAMPLE_SLOT.id))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmScheduled clears selectedSlot after success`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSlotSelected(SAMPLE_SLOT)
            awaitItem()

            viewModel.onConfirmScheduled()
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()

            assertNull(state.selectedSlot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmScheduled without a selected slot is a no-op`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            // No slot selected — confirm should silently do nothing
            viewModel.onConfirmScheduled()
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
            assertNull(fakeOrderRepo.lastPlacedOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmScheduled on empty cart is a no-op`() = runTest {
        // Cart is empty by default
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSlotSelected(SAMPLE_SLOT)
            awaitItem()

            viewModel.onConfirmScheduled()
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
            assertNull(fakeOrderRepo.lastPlacedOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── placed order visible in order flow ────────────────────────────────────

    @Test
    fun `placed scheduled order is retrievable with correct type`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSlotSelected(SAMPLE_SLOT)
            awaitItem()

            viewModel.onConfirmScheduled()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()

            val orders = fakeOrderRepo.ordersForShopper(SAMPLE_USER.id)
            assertTrue(orders.any { it.orderType == OrderType.SCHEDULED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `placed on-demand order is retrievable with correct type`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onDeliverNow()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()

            val orders = fakeOrderRepo.ordersForShopper(SAMPLE_USER.id)
            assertTrue(orders.any { it.orderType == OrderType.ON_DEMAND })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onCheckoutSuccessConsumed ─────────────────────────────────────────────

    @Test
    fun `onCheckoutSuccessConsumed clears checkoutSuccess flag`() = runTest {
        fakeCartRepo.addItem(SAMPLE_PRODUCT)

        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onDeliverNow()
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // checkoutSuccess = true

            viewModel.onCheckoutSuccessConsumed()
            val state = awaitItem()

            assertFalse(state.checkoutSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun app.cash.turbine.TurbineTestContext<CartUiState>.awaitReadyState(): CartUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }
}

// ─── Sample data ──────────────────────────────────────────────────────────────

private val SAMPLE_USER = User(
    id = "user-test", name = "Test User", email = "test@test.com",
    role = UserRole.SHOPPER, deliveryAddress = "1 Test St",
)

private val SAMPLE_PRODUCT = Product(
    id = "p1", name = "Apple", price = BigDecimal("1.99"),
    imageUrl = "", category = "Produce", unit = ProductUnit.PIECE,
)

private val SAMPLE_SLOT = DeliverySlot(
    id = "2026-02-18_MORNING",
    date = LocalDate.of(2026, 2, 18),
    type = SlotType.MORNING,
    bookedCount = 0,
    capacity = 10,
)

// ─── Fakes ────────────────────────────────────────────────────────────────────

private class FakeCartRepository : CartRepository {
    private val _items = MutableStateFlow<Map<String, Int>>(emptyMap())
    override fun observeCartItems(): Flow<Map<String, Int>> = _items.asStateFlow()
    override fun getCartItemsSnapshot() = _items.value
    override suspend fun addItem(product: Product) {
        _items.update { it + (product.id to (it[product.id] ?: 0) + 1) }
    }
    override suspend fun removeItem(productId: String) {
        _items.update { current ->
            val qty = current[productId] ?: return@update current
            if (qty <= 1) current - productId else current + (productId to qty - 1)
        }
    }
    override suspend fun removeItemCompletely(productId: String) {
        _items.update { it - productId }
    }
    override suspend fun clearCart() { _items.value = emptyMap() }
}

private class FakeProductRepository : ProductRepository {
    private val _products = MutableStateFlow(listOf(SAMPLE_PRODUCT))
    override fun observeProducts(): Flow<List<Product>> = _products
    override fun observeProductsByCategory(category: String) =
        _products.map { it.filter { p -> p.category == category } }
    override fun observeCategories() =
        _products.map { it.map { p -> p.category }.distinct() }
    override suspend fun getProduct(id: String) = _products.value.find { it.id == id }
    override suspend fun searchProducts(query: String) =
        _products.value.filter { it.name.contains(query, ignoreCase = true) }
}

private class FakeOrderRepository : OrderRepository {
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    var lastPlacedOrder: Order? = null

    fun ordersForShopper(shopperId: String) =
        _orders.value.filter { it.shopperId == shopperId }

    override fun observeOrdersForShopper(shopperId: String) =
        _orders.map { it.filter { o -> o.shopperId == shopperId } }

    override suspend fun getOrder(orderId: String) =
        _orders.value.find { it.orderId == orderId }

    override suspend fun placeOrder(order: Order): Result<Order> {
        lastPlacedOrder = order
        _orders.update { it + order }
        return Result.success(order)
    }

    override suspend fun cancelOrder(orderId: String, reason: String) =
        updateOrderStatus(orderId, OrderStatus.CANCELLED, "user", reason)

    override suspend fun updateOrderStatus(
        orderId: String, newStatus: OrderStatus, changedBy: String, reason: String?,
    ): Result<Order> {
        val order = _orders.value.find { it.orderId == orderId }
            ?: return Result.failure(IllegalArgumentException("Not found"))
        val updated = order.copy(status = newStatus)
        _orders.update { it.map { o -> if (o.orderId == orderId) updated else o } }
        return Result.success(updated)
    }
}

private class FakeUserRepository : UserRepository {
    private val _user = MutableStateFlow<User?>(SAMPLE_USER)
    override fun observeCurrentUser(): Flow<User?> = _user
    override suspend fun getCurrentUser() = _user.value
    override suspend fun updateUser(user: User): Result<User> {
        _user.value = user
        return Result.success(user)
    }
}

private class FakeSlotRepository : SlotRepository {
    private val _slots = MutableStateFlow<Map<String, DeliverySlot>>(
        generateSlots()
    )
    private val _bookCounts = mutableMapOf<String, Int>()

    fun seedSlot(slot: DeliverySlot) {
        _slots.update { it + (slot.id to slot) }
    }

    fun bookCallCount(slotId: String): Int = _bookCounts[slotId] ?: 0

    override fun observeAvailableSlots(from: LocalDate, days: Int): Flow<List<DeliverySlot>> {
        val window = (0 until days).map { from.plusDays(it.toLong()) }.toSet()
        return _slots.map { all ->
            all.values
                .filter { it.date in window }
                .sortedWith(compareBy({ it.date }, { it.type }))
        }
    }

    override suspend fun bookSlot(slotId: String): Result<DeliverySlot> {
        val slot = _slots.value[slotId]
            ?: return Result.failure(IllegalArgumentException("Not found: $slotId"))
        _bookCounts[slotId] = (_bookCounts[slotId] ?: 0) + 1
        val updated = slot.copy(bookedCount = slot.bookedCount + 1)
        _slots.update { it + (slotId to updated) }
        return Result.success(updated)
    }

    private companion object {
        fun generateSlots(): Map<String, DeliverySlot> {
            val today = LocalDate.now()
            return (0 until DeliverySlot.AVAILABLE_DAYS)
                .flatMap { DeliverySlot.generateForDate(today.plusDays(it.toLong())) }
                .associateBy { it.id }
        }
    }
}