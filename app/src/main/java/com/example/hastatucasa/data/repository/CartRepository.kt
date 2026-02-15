package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

interface CartRepository {
    /** Live map of productId → quantity for all items currently in the cart. */
    fun observeCartItems(): Flow<Map<String, Int>>

    /** Current snapshot (non-suspending convenience for ViewModels that don't need reactivity). */
    fun getCartItemsSnapshot(): Map<String, Int>

    suspend fun addItem(product: Product)
    suspend fun removeItem(productId: String)

    /** Remove a product entirely regardless of quantity. */
    suspend fun removeItemCompletely(productId: String)

    suspend fun clearCart()
}

@Singleton
class FakeCartRepository @Inject constructor() : CartRepository {

    private val _items = MutableStateFlow<Map<String, Int>>(emptyMap())

    override fun observeCartItems(): Flow<Map<String, Int>> = _items.asStateFlow()

    override fun getCartItemsSnapshot(): Map<String, Int> = _items.value

    override suspend fun addItem(product: Product) {
        _items.update { current ->
            current + (product.id to (current[product.id] ?: 0) + 1)
        }
    }

    override suspend fun removeItem(productId: String) {
        _items.update { current ->
            val existing = current[productId] ?: return@update current
            if (existing <= 1) current - productId
            else current + (productId to existing - 1)
        }
    }

    override suspend fun removeItemCompletely(productId: String) {
        _items.update { it - productId }
    }

    override suspend fun clearCart() {
        _items.value = emptyMap()
    }
}