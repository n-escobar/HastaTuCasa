package com.example.hastatucasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.hastatucasa.ui.navigation.HastaTuCasaNavHost
import com.example.hastatucasa.ui.theme.HastaTuCasaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for the **shopper** product flavor.
 * Launches the standard shopper navigation graph (Browse / Cart / Profile).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HastaTuCasaTheme {
                HastaTuCasaNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}