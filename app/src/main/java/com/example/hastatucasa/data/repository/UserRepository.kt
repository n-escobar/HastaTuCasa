package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.User
import com.example.hastatucasa.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    fun observeCurrentUser(): Flow<User?>
    suspend fun getCurrentUser(): User?
    suspend fun updateUser(user: User): Result<User>
}

@Singleton
class FakeUserRepository @Inject constructor() : UserRepository {

    private val _currentUser = MutableStateFlow<User?>(
        User(
            id = "user-shopper",
            name = "Alex Johnson",
            email = "alex.johnson@email.com",
            role = UserRole.SHOPPER,
            avatarUrl = null,
            deliveryAddress = "123 Main St, Springfield, IL 62701"
        )
    )

    override fun observeCurrentUser(): Flow<User?> = _currentUser.asStateFlow()

    override suspend fun getCurrentUser(): User? = _currentUser.value

    override suspend fun updateUser(user: User): Result<User> {
        _currentUser.value = user
        return Result.success(user)
    }
}