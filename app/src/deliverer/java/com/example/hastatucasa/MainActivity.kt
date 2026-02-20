package com.example.hastatucasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.hastatucasa.ui.navigation.DelivererNavHost
import com.example.hastatucasa.ui.theme.HastaTuCasaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for the **deliverer** product flavor.
 * Launches the deliverer navigation graph (Orders queue / Profile).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HastaTuCasaTheme {
                DelivererNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}