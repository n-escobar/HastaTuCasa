package com.example.hastatucasa.data.repository

import com.example.hastatucasa.data.model.User
import com.example.hastatucasa.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [UserRepository] backed by Firebase Auth + Cloud Firestore.
 *
 * Firestore schema
 * ─────────────────
 * users/{uid}
 *   id              : String   ← same as Auth UID
 *   name            : String
 *   email           : String
 *   role            : String   ← UserRole.name  ("SHOPPER" | "DELIVERER")
 *   avatarUrl       : String?
 *   deliveryAddress : String
 *   fcmToken        : String?  ← updated by [FirebaseMessagingRepository]
 */
@Singleton
class FirebaseUserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : UserRepository {

    private val usersCol = firestore.collection("users")

    // ── observe ───────────────────────────────────────────────────────────────

    /**
     * Emits the current user document whenever Auth state or the Firestore
     * document changes. Emits null when signed out.
     */
    override fun observeCurrentUser(): Flow<User?> =
        authStateFlow().flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else usersCol.document(uid).snapshots().map { it.toUser() }
        }

    // ── read ──────────────────────────────────────────────────────────────────

    override suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return usersCol.document(uid).get().await().toUser()
    }

    // ── write ─────────────────────────────────────────────────────────────────

    override suspend fun updateUser(user: User): Result<User> = runCatching {
        usersCol.document(user.id).set(user.toMap()).await()
        user
    }

    /**
     * Creates or overwrites the Firestore user document for a newly signed-in
     * account. Call this from your sign-in / sign-up flow after Auth succeeds.
     *
     * If the document already exists (returning user), this is a no-op merge so
     * existing fields like `deliveryAddress` are preserved.
     */
    suspend fun createUserIfAbsent(uid: String, email: String, role: UserRole): Result<User> =
        runCatching {
            val docRef = usersCol.document(uid)
            val snap   = docRef.get().await()

            if (snap.exists()) {
                snap.toUser() ?: error("Could not parse existing user $uid")
            } else {
                val newUser = User(
                    id    = uid,
                    name  = email.substringBefore("@").replaceFirstChar { it.uppercaseChar() },
                    email = email,
                    role  = role,
                )
                docRef.set(newUser.toMap()).await()
                newUser
            }
        }

    // ── serialisation ─────────────────────────────────────────────────────────

    private fun User.toMap(): Map<String, Any?> = mapOf(
        "id"              to id,
        "name"            to name,
        "email"           to email,
        "role"            to role.name,
        "avatarUrl"       to avatarUrl,
        "deliveryAddress" to deliveryAddress,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User? {
        if (!exists()) return null
        return try {
            User(
                id              = getString("id") ?: id,
                name            = getString("name") ?: "",
                email           = getString("email") ?: "",
                role            = UserRole.valueOf(getString("role") ?: "SHOPPER"),
                avatarUrl       = getString("avatarUrl"),
                deliveryAddress = getString("deliveryAddress") ?: "",
            )
        } catch (e: Exception) { null }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /** Cold flow that emits the current Auth UID (or null) whenever sign-in state changes. */
    private fun authStateFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}