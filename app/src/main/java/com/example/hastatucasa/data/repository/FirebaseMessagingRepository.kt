package com.example.hastatucasa.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.hastatucasa.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// ─── Channel constants ────────────────────────────────────────────────────────

private const val CHANNEL_ORDER_UPDATES  = "order_updates"
private const val CHANNEL_NEW_ORDERS     = "new_orders"

// ─── Repository ───────────────────────────────────────────────────────────────

/**
 * Manages the device FCM token lifecycle.
 *
 * Responsibilities:
 *  1. Fetch and persist the FCM token to Firestore when the user signs in.
 *  2. Refresh the token whenever FCM rotates it ([HastaTuCasaMessagingService]).
 *  3. Create the notification channels required on Android 8+.
 */
@Singleton
class FirebaseMessagingRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    private val usersCol = firestore.collection("users")

    /** Fetch the current token and write it to the signed-in user's document. */
    suspend fun refreshAndPersistToken() {
        val uid = auth.currentUser?.uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        persistToken(uid, token)
    }

    /** Write [token] to `users/{uid}/fcmToken`. Called on token refresh and sign-in. */
    suspend fun persistToken(uid: String, token: String) {
        usersCol.document(uid).update("fcmToken", token).await()
    }

    /** Create notification channels. Call from [Application.onCreate]. */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ORDER_UPDATES,
                "Order Updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications about your order status changes"
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEW_ORDERS,
                "New Orders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming orders for deliverers"
            }
        )
    }
}

// ─── FCM Service ──────────────────────────────────────────────────────────────

/**
 * Receives FCM messages and token refreshes.
 *
 * Declare in AndroidManifest.xml inside <application>:
 *
 * ```xml
 * <service
 *     android:name=".data.repository.HastaTuCasaMessagingService"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *     </intent-filter>
 * </service>
 * ```
 *
 * Push payload schema (sent from your server / Cloud Functions):
 * ```json
 * {
 *   "data": {
 *     "type"    : "ORDER_STATUS_UPDATE" | "NEW_ORDER",
 *     "orderId" : "<orderId>",
 *     "title"   : "Your order is on its way!",
 *     "body"    : "Estimated arrival in 20 minutes"
 *   }
 * }
 * ```
 * Using `data` (not `notification`) payloads ensures the message is always
 * delivered to [onMessageReceived] even when the app is in the background.
 */
@AndroidEntryPoint
class HastaTuCasaMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var messagingRepository: FirebaseMessagingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── token refresh ─────────────────────────────────────────────────────────

    /**
     * Called by FCM when the token changes (app reinstall, token expiry, etc.).
     * Write the new token to Firestore so the server can target this device.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        serviceScope.launch {
            messagingRepository.persistToken(uid, token)
        }
    }

    // ── message received ──────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data    = message.data
        val type    = data["type"] ?: return
        val orderId = data["orderId"] ?: return
        val title   = data["title"] ?: appName()
        val body    = data["body"] ?: return

        val channel = when (type) {
            "NEW_ORDER" -> CHANNEL_NEW_ORDERS
            else        -> CHANNEL_ORDER_UPDATES
        }

        showNotification(
            notificationId = orderId.hashCode(),
            channelId      = channel,
            title          = title,
            body           = body,
            orderId        = orderId,
        )
    }

    // ── notification builder ──────────────────────────────────────────────────

    private fun showNotification(
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        orderId: String,
    ) {
        // Deep-link intent: opens MainActivity; the NavHost can read the
        // orderId extra to navigate to the relevant order detail screen.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("orderId", orderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)  // replace with your app icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        }
    }

    private fun appName(): String =
        applicationInfo.loadLabel(packageManager).toString()
}