package com.example.hastatucasa

import android.app.Application
import com.example.hastatucasa.data.repository.FirebaseMessagingRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class.
 *
 * Changes from original:
 *  • Injects [FirebaseMessagingRepository] to create notification channels
 *    at startup (required on Android 8+; safe to call on older versions).
 */
@HiltAndroidApp
class HastaTuCasa : Application() {

    @Inject
    lateinit var messagingRepository: FirebaseMessagingRepository

    override fun onCreate() {
        super.onCreate()
        messagingRepository.createNotificationChannels(this)
    }
}