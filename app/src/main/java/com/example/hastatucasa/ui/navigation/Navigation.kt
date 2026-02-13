package com.example.hastatucasa.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hastatucasa.ui.browse.BrowseScreen
import com.example.hastatucasa.ui.profile.ProfileScreen

// ─── Routes ───────────────────────────────────────────────────────────────────

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    object Browse : Screen("browse", "Shop",    Icons.Filled.Storefront,  Icons.Outlined.Storefront)
    object Profile : Screen("profile", "Profile", Icons.Filled.Person,   Icons.Outlined.PersonOutline)

    companion object {
        val bottomNavItems = listOf(Browse, Profile)
    }
}

// ─── Root Nav ─────────────────────────────────────────────────────────────────

@Composable
fun HastaTuCasaNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { screen ->
                    val isSelected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label,
                            )
                        },
                        label = { Text(screen.label) },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Browse.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) {
            composable(Screen.Browse.route) {
                BrowseScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen()
            }
        }
    }
}