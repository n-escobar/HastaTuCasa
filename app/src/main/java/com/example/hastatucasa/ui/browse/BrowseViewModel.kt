package com.example.hastatucasa.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hastatucasa.data.model.Product
import com.example.hastatucasa.data.repository.CartRepository
import com.example.hastatucasa.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── State ────────────────────────────────────────────────────────────────────

data class BrowseUiState(
    val isLoading: Boolean = true,
    val categories: List<String> = emptyList(),
    val products: List<Product> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val cartItems: Map<String, Int> = emptyMap(),
    val snackbarMessage: String? = null,
)

val BrowseUiState.cartTotal: Int get() = cartItems.values.sum()

// ─── ViewModel ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val cartRepository: CartRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    private val debouncedQuery = _searchQuery.debounce(300L)

    private val filteredProducts = combine(
        _selectedCategory
            .flatMapLatest { category ->
                if (category != null)
                    productRepository.observeProductsByCategory(category)
                else
                    productRepository.observeProducts()
            },
        debouncedQuery,
    ) { products, query ->
        if (query.isBlank()) products
        else products.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
        }
    }

    val uiState: StateFlow<BrowseUiState> = combine(
        productRepository.observeCategories(),
        filteredProducts,
        _selectedCategory,
        _searchQuery,
        combine(
            cartRepository.observeCartItems(),
            _snackbarMessage,
        ) { cart, snackbar -> cart to snackbar },
    ) { categories, products, selectedCategory, searchQuery, (cartItems, snackbarMessage) ->
        BrowseUiState(
            isLoading = false,
            categories = categories,
            products = products,
            selectedCategory = selectedCategory,
            searchQuery = searchQuery,
            cartItems = cartItems,
            snackbarMessage = snackbarMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowseUiState(),
    )

    // ─── Intents ──────────────────────────────────────────────────────────────

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = when {
            category == null -> null
            _selectedCategory.value == category -> null
            else -> category
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onAddToCart(product: Product) {
        viewModelScope.launch {
            cartRepository.addItem(product)
            _snackbarMessage.value = "${product.name} added to cart"
        }
    }

    fun onRemoveFromCart(productId: String) {
        viewModelScope.launch {
            cartRepository.removeItem(productId)
        }
    }

    fun onSnackbarDismissed() {
        _snackbarMessage.value = null
    }
}