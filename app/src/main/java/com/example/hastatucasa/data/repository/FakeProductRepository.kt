package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.Product
import com.example.hastatucasa.data.model.ProductUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeProductRepository @Inject constructor() : ProductRepository {

    private val products = MutableStateFlow(SAMPLE_PRODUCTS)

    override fun observeProducts(): Flow<List<Product>> = products

    override fun observeProductsByCategory(category: String): Flow<List<Product>> =
        products.map { list -> list.filter { it.category == category } }

    override fun observeCategories(): Flow<List<String>> =
        products.map { list -> list.map { it.category }.distinct().sorted() }

    override suspend fun getProduct(id: String): Product? =
        products.value.find { it.id == id }

    override suspend fun searchProducts(query: String): List<Product> =
        products.value.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
        }

    companion object {
        val SAMPLE_PRODUCTS = listOf(
            // Produce
            Product("p1",  "Organic Fuji Apples",    BigDecimal("3.99"),  "https://images.unsplash.com/photo-1570913149827-d2ac84ab3f9a?w=400", "Produce",  ProductUnit.LB,    BigDecimal("10")),
            Product("p2",  "Bananas",                BigDecimal("0.59"),  "https://images.unsplash.com/photo-1528825871115-3581a5387919?w=400", "Produce",  ProductUnit.LB),
            Product("p3",  "Baby Spinach 5oz",       BigDecimal("4.49"),  "https://images.unsplash.com/photo-1576045057995-568f588f82fb?w=400", "Produce",  ProductUnit.PIECE, BigDecimal("5")),
            Product("p4",  "Avocados",               BigDecimal("1.29"),  "https://images.unsplash.com/photo-1519162808019-7de1683fa2ad?w=400", "Produce",  ProductUnit.PIECE),
            Product("p5",  "Roma Tomatoes",          BigDecimal("2.49"),  "https://images.unsplash.com/photo-1546094096-0df4bcaaa337?w=400", "Produce",  ProductUnit.LB),
            Product("p6",  "Broccoli Crown",         BigDecimal("2.29"),  "https://images.unsplash.com/photo-1459411621453-7b03977f4bfc?w=400", "Produce",  ProductUnit.PIECE),
            // Dairy
            Product("d1",  "Whole Milk 1gal",        BigDecimal("5.49"),  "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400", "Dairy",    ProductUnit.PIECE),
            Product("d2",  "Greek Yogurt Plain",     BigDecimal("6.99"),  "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=400", "Dairy",    ProductUnit.PIECE, BigDecimal("15")),
            Product("d3",  "Sharp Cheddar Block",    BigDecimal("7.99"),  "https://images.unsplash.com/photo-1559561853-08451507cbe7?w=400", "Dairy",    ProductUnit.PIECE),
            Product("d4",  "Large Eggs 12ct",        BigDecimal("4.79"),  "https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f?w=400", "Dairy",    ProductUnit.PIECE),
            // Bakery
            Product("b1",  "Sourdough Loaf",         BigDecimal("5.99"),  "https://images.unsplash.com/photo-1585478259715-876acc5be8eb?w=400", "Bakery",   ProductUnit.PIECE),
            Product("b2",  "Blueberry Muffins 4pk",  BigDecimal("4.99"),  "https://images.unsplash.com/photo-1607958996333-41aef7caefaa?w=400", "Bakery",   ProductUnit.PIECE, BigDecimal("20")),
            Product("b3",  "Croissants 4pk",         BigDecimal("5.49"),  "https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=400", "Bakery",   ProductUnit.PIECE),
            // Meat
            Product("m1",  "Chicken Breast",         BigDecimal("6.99"),  "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=400", "Meat",     ProductUnit.LB),
            Product("m2",  "Ground Beef 80/20",      BigDecimal("5.99"),  "https://images.unsplash.com/photo-1588347818036-c8ca7d3c5dff?w=400", "Meat",     ProductUnit.LB,    BigDecimal("8")),
            Product("m3",  "Salmon Fillet",          BigDecimal("12.99"), "https://images.unsplash.com/photo-1574781330855-d0db8cc6a79c?w=400", "Meat",     ProductUnit.LB),
            // Pantry
            Product("n1",  "Pasta Spaghetti 1lb",    BigDecimal("2.29"),  "https://images.unsplash.com/photo-1556761223-4c4282c73f77?w=400", "Pantry",   ProductUnit.PIECE),
            Product("n2",  "Olive Oil Extra Virgin",  BigDecimal("10.99"), "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=400", "Pantry",   ProductUnit.PIECE),
            Product("n3",  "Canned Tomatoes 28oz",   BigDecimal("2.99"),  "https://images.unsplash.com/photo-1556909212-d5b604d0c90d?w=400", "Pantry",   ProductUnit.PIECE),
            Product("n4",  "Rolled Oats 42oz",       BigDecimal("7.49"),  "https://images.unsplash.com/photo-1614961908044-eee3c34b5d26?w=400", "Pantry",   ProductUnit.PIECE, BigDecimal("12")),
            // Beverages
            Product("v1",  "Orange Juice 52oz",      BigDecimal("5.99"),  "https://images.unsplash.com/photo-1600271886742-f049cd451bba?w=400", "Beverages",ProductUnit.PIECE),
            Product("v2",  "Sparkling Water 12pk",   BigDecimal("8.99"),  "https://images.unsplash.com/photo-1564419320461-6870880221ad?w=400", "Beverages",ProductUnit.PIECE, BigDecimal("10")),
            Product("v3",  "Cold Brew Coffee",       BigDecimal("4.49"),  "https://images.unsplash.com/photo-1461023058943-07fcbe16d735?w=400", "Beverages",ProductUnit.PIECE),
        )
    }
}