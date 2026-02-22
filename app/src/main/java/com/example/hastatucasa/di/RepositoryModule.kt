package com.example.hastatucasa.di

import com.example.hastatucasa.data.repository.CartRepository
import com.example.hastatucasa.data.repository.DelivererOrderRepository
import com.example.hastatucasa.data.repository.FakeCartRepository
import com.example.hastatucasa.data.repository.FirebaseAuthRepository
import com.example.hastatucasa.data.repository.FirebaseDelivererOrderRepository
import com.example.hastatucasa.data.repository.FirebaseOrderRepository
import com.example.hastatucasa.data.repository.FirebaseUserRepository
import com.example.hastatucasa.data.repository.OrderRepository
import com.example.hastatucasa.data.repository.SlotRepository
import com.example.hastatucasa.data.repository.FakeSlotRepository
import com.example.hastatucasa.data.repository.UserRepository
import com.example.hastatucasa.data.repository.FakeProductRepository
import com.example.hastatucasa.data.repository.ProductRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.ktx.messaging
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Production DI module.
 *
 * Strategy
 * ────────
 * • Firebase-backed repositories replace all Fake* implementations.
 * • [FakeCartRepository] is kept because cart state is intentionally
 *   ephemeral (lives only for the checkout session). Swap it for a
 *   Room or Firestore implementation when you want persistent cart state.
 * • [FakeSlotRepository] is kept until your backend exposes real slot
 *   management. Replace it with a FirebaseSlotRepository that reads from
 *   a `slots` collection when ready.
 *
 * Tests
 * ─────
 * Unit tests use hand-rolled fakes directly (see BrowseViewModelTest etc.).
 * This module is never loaded in the test source set, so no additional
 * Hilt test module is required.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // ── Firebase SDK singletons ───────────────────────────────────────────────

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = Firebase.messaging
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // ── Firebase-backed repositories ──────────────────────────────────────────

    @Binds @Singleton
    abstract fun bindOrderRepository(
        impl: FirebaseOrderRepository,
    ): OrderRepository

    @Binds @Singleton
    abstract fun bindUserRepository(
        impl: FirebaseUserRepository,
    ): UserRepository

    @Binds @Singleton
    abstract fun bindDelivererOrderRepository(
        impl: FirebaseDelivererOrderRepository,
    ): DelivererOrderRepository

    // ── Kept as in-memory for now ─────────────────────────────────────────────

    @Binds @Singleton
    abstract fun bindCartRepository(
        impl: FakeCartRepository,
    ): CartRepository

    @Binds @Singleton
    abstract fun bindSlotRepository(
        impl: FakeSlotRepository,
    ): SlotRepository

    @Binds @Singleton
    abstract fun bindProductRepository(
        impl: FakeProductRepository,
    ): ProductRepository
}