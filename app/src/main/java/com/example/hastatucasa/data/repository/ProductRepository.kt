package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
    fun observeProductsByCategory(category: String): Flow<List<Product>>
    fun observeCategories(): Flow<List<String>>
    suspend fun getProduct(id: String): Product?
    suspend fun searchProducts(query: String): List<Product>
}