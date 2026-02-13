package com.example.hastatucasa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenPrimary       = Color(0xFF2E7D32)
private val GreenOnPrimary     = Color(0xFFFFFFFF)
private val GreenContainer     = Color(0xFFC8E6C9)
private val GreenOnContainer   = Color(0xFF003909)
private val OrangeSecondary    = Color(0xFFE65100)
private val OrangeContainer    = Color(0xFFFFE0B2)

private val GroceryColorScheme = lightColorScheme(
    primary          = GreenPrimary,
    onPrimary        = GreenOnPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary        = OrangeSecondary,
    secondaryContainer = OrangeContainer,
    background       = Color(0xFFF9FBF9),
    surface          = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFFF1F4F1),
    outline          = Color(0xFF9E9E9E),
)

@Composable
fun HastaTuCasaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GroceryColorScheme,
        content = content,
    )
}