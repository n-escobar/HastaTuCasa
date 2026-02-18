package com.example.hastatucasa.di

import com.example.hastatucasa.data.repository.FakeCartRepository
import com.example.hastatucasa.data.repository.FakeOrderRepository
import com.example.hastatucasa.data.repository.FakeProductRepository
import com.example.hastatucasa.data.repository.FakeSlotRepository
import com.example.hastatucasa.data.repository.FakeUserRepository
import com.example.hastatucasa.data.repository.CartRepository
import com.example.hastatucasa.data.repository.OrderRepository
import com.example.hastatucasa.data.repository.ProductRepository
import com.example.hastatucasa.data.repository.SlotRepository
import com.example.hastatucasa.data.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: FakeProductRepository): ProductRepository

    @Binds
    @Singleton
    abstract fun bindOrderRepository(impl: FakeOrderRepository): OrderRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: FakeUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindCartRepository(impl: FakeCartRepository): CartRepository

    @Binds
    @Singleton
    abstract fun bindSlotRepository(impl: FakeSlotRepository): SlotRepository
}