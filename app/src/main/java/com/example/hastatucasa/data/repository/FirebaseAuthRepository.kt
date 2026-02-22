package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.User
import com.example.hastatucasa.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Firebase Auth email/password sign-in and sign-up.
 *
 * After a successful Auth operation, the Firestore user document is
 * created/fetched via [FirebaseUserRepository.createUserIfAbsent].
 *
 * Error handling
 * ──────────────
 * All public functions return [Result]; callers should use
 * `onSuccess` / `onFailure` rather than catching exceptions.
 * User-friendly messages are produced for the most common Firebase
 * error codes so the UI layer never needs to inspect exception types.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: FirebaseUserRepository,
) {
    val isSignedIn: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    // ── sign-up ───────────────────────────────────────────────────────────────

    /**
     * Creates a new Auth account and a matching Firestore user document.
     *
     * @param role Pass [UserRole.SHOPPER] from the shopper flavor entry point
     *             and [UserRole.DELIVERER] from the deliverer entry point.
     */
    suspend fun signUp(
        email: String,
        password: String,
        role: UserRole,
    ): Result<User> = runCatching {
        val credential = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = credential.user?.uid ?: error("Auth returned null uid")
        userRepository.createUserIfAbsent(uid, email, role).getOrThrow()
    }.mapFirebaseErrors()

    // ── sign-in ───────────────────────────────────────────────────────────────

    suspend fun signIn(email: String, password: String): Result<User> = runCatching {
        val credential = auth.signInWithEmailAndPassword(email, password).await()
        val uid = credential.user?.uid ?: error("Auth returned null uid")
        // Ensure document exists for users created before this code was deployed
        userRepository.createUserIfAbsent(uid, email, UserRole.SHOPPER).getOrThrow()
    }.mapFirebaseErrors()

    // ── sign-out ──────────────────────────────────────────────────────────────

    fun signOut() = auth.signOut()

    // ── password reset ────────────────────────────────────────────────────────

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }.mapFirebaseErrors()

    // ── error mapping ─────────────────────────────────────────────────────────

    /**
     * Converts Firebase-specific exceptions into plain [IllegalStateException]
     * with human-readable messages, so the UI layer stays Firebase-agnostic.
     */
    private fun <T> Result<T>.mapFirebaseErrors(): Result<T> = recoverCatching { e ->
        throw when (e) {
            is FirebaseAuthInvalidCredentialsException ->
                IllegalStateException("Incorrect email or password.")
            is FirebaseAuthInvalidUserException ->
                IllegalStateException("No account found for that email address.")
            is FirebaseAuthUserCollisionException ->
                IllegalStateException("An account with that email already exists.")
            else -> e
        }
    }
}