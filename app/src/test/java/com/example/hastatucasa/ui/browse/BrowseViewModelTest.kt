package com.example.hastatucasa.ui.browse

import app.cash.turbine.test
import com.example.hastatucasa.data.model.Product
import com.example.hastatucasa.data.model.ProductUnit
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
import com.example.hastatucasa.data.repository.ProductRepository

/**
 * Tests for BrowseViewModel.
 *
 * Uses a hand-rolled fake ProductRepository (no Mockito needed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {
    //20 TESTS IN THIS CLASS
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeProductRepository
    private lateinit var viewModel: BrowseViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeProductRepository()
        //viewModel = BrowseViewModel(fakeRepo)
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
    fun `after first emission isLoading becomes false`() = runTest {
        viewModel.uiState.test {
            skipItems(1) // loading state
            val ready = awaitItem()
            assertFalse(ready.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `all products appear when no filter or search`() = runTest {
        viewModel.uiState.test {
            val state = awaitReadyState()
            assertEquals(SAMPLE_PRODUCTS.size, state.products.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── category filter ───────────────────────────────────────────────────────

    @Test
    fun `selecting a category filters products`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onCategorySelected("Produce")
            val filtered = awaitItem()

            assertTrue(filtered.products.all { it.category == "Produce" })
            assertFalse(filtered.products.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-tapping selected category deselects it`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onCategorySelected("Produce")
            awaitItem() // filtered

            viewModel.onCategorySelected("Produce")
            val deselected = awaitItem()

            assertNull(deselected.selectedCategory)
            assertEquals(SAMPLE_PRODUCTS.size, deselected.products.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching category replaces filter`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onCategorySelected("Produce")
            awaitItem()

            viewModel.onCategorySelected("Dairy")
            val state = awaitItem()

            assertEquals("Dairy", state.selectedCategory)
            assertTrue(state.products.all { it.category == "Dairy" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting null clears filter`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onCategorySelected("Produce")
            awaitItem()

            viewModel.onCategorySelected(null)
            val cleared = awaitItem()

            assertNull(cleared.selectedCategory)
            assertEquals(SAMPLE_PRODUCTS.size, cleared.products.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `categories list contains all distinct categories`() = runTest {
        viewModel.uiState.test {
            val state = awaitReadyState()
            val expected = SAMPLE_PRODUCTS.map { it.category }.distinct().sorted()
            assertEquals(expected, state.categories)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    fun `search by name filters products`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSearchQueryChanged("apple")
            testDispatcher.scheduler.advanceTimeBy(350) // past debounce
            val state = awaitItem()

            assertTrue(state.products.all { "apple" in it.name.lowercase() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty search shows all products`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSearchQueryChanged("apple")
            testDispatcher.scheduler.advanceTimeBy(350)
            awaitItem()

            viewModel.onSearchQueryChanged("")
            testDispatcher.scheduler.advanceTimeBy(350)
            val state = awaitItem()

            assertEquals(SAMPLE_PRODUCTS.size, state.products.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search with no matches returns empty list`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onSearchQueryChanged("xyznotaproduct")
            testDispatcher.scheduler.advanceTimeBy(350)
            val state = awaitItem()

            assertTrue(state.products.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── cart ──────────────────────────────────────────────────────────────────

    @Test
    fun `cart starts empty`() = runTest {
        viewModel.uiState.test {
            val state = awaitReadyState()
            assertTrue(state.cartItems.isEmpty())
            assertEquals(0, state.cartTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding product increments quantity`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            val product = SAMPLE_PRODUCTS[0]
            viewModel.onAddToCart(product)
            val state = awaitItem()

            assertEquals(1, state.cartItems[product.id])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding same product twice reaches quantity 2`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            val product = SAMPLE_PRODUCTS[0]
            viewModel.onAddToCart(product)
            awaitItem()

            viewModel.onAddToCart(product)
            val state = awaitItem()

            assertEquals(2, state.cartItems[product.id])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing product decrements quantity`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            val product = SAMPLE_PRODUCTS[0]
            viewModel.onAddToCart(product)
            awaitItem()
            viewModel.onAddToCart(product)
            awaitItem()

            viewModel.onRemoveFromCart(product.id)
            val state = awaitItem()

            assertEquals(1, state.cartItems[product.id])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing last unit removes product from cart entirely`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            val product = SAMPLE_PRODUCTS[0]
            viewModel.onAddToCart(product)
            awaitItem()

            viewModel.onRemoveFromCart(product.id)
            val state = awaitItem()

            assertFalse(product.id in state.cartItems)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing from empty cart is a no-op`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onRemoveFromCart("p-nonexistent")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cartTotal is sum of all quantities`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onAddToCart(SAMPLE_PRODUCTS[0])
            awaitItem()
            viewModel.onAddToCart(SAMPLE_PRODUCTS[1])
            awaitItem()
            viewModel.onAddToCart(SAMPLE_PRODUCTS[0])
            val state = awaitItem()

            assertEquals(3, state.cartTotal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── snackbar ──────────────────────────────────────────────────────────────

    @Test
    fun `adding product shows snackbar with product name`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            val product = SAMPLE_PRODUCTS[0]
            viewModel.onAddToCart(product)
            val state = awaitItem()

            assertTrue(state.snackbarMessage?.contains(product.name) == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing snackbar clears message`() = runTest {
        viewModel.uiState.test {
            awaitReadyState()

            viewModel.onAddToCart(SAMPLE_PRODUCTS[0])
            awaitItem()

            viewModel.onSnackbarDismissed()
            val state = awaitItem()

            assertNull(state.snackbarMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Skips the initial loading state and returns the first ready state. */
    private suspend fun app.cash.turbine.TurbineTestContext<BrowseUiState>.awaitReadyState(): BrowseUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }
}


// ─── Fake Repository ──────────────────────────────────────────────────────────

private val SAMPLE_PRODUCTS = listOf(
    product("p1", "Organic Fuji Apples", "Produce"),
    product("p2", "Bananas",             "Produce"),
    product("d1", "Whole Milk 1gal",     "Dairy"),
    product("d2", "Greek Yogurt",        "Dairy"),
    product("b1", "Sourdough Loaf",      "Bakery"),
)

private fun product(id: String, name: String, category: String) = Product(
    id = id, name = name, price = BigDecimal("1.99"),
    imageUrl = "", category = category, unit = ProductUnit.PIECE,
)

private class FakeProductRepository : ProductRepository {
    private val _products = MutableStateFlow(SAMPLE_PRODUCTS)

    override fun observeProducts(): Flow<List<Product>> = _products

    override fun observeProductsByCategory(category: String): Flow<List<Product>> =
        _products.map { it.filter { p -> p.category == category } }

    override fun observeCategories(): Flow<List<String>> =
        _products.map { it.map { p -> p.category }.distinct().sorted() }

    override suspend fun getProduct(id: String): Product? =
        _products.value.find { it.id == id }

    override suspend fun searchProducts(query: String): List<Product> =
        _products.value.filter { it.name.contains(query, ignoreCase = true) }
}