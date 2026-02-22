package com.example.hastatucasa.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hastatucasa.data.model.UserRole
import com.example.hastatucasa.ui.auth.AuthScreen
import com.example.hastatucasa.ui.deliverer.DelivererOrdersScreen
import com.example.hastatucasa.ui.profile.ProfileScreen

// ─── Deliverer routes ─────────────────────────────────────────────────────────

sealed class DelivererScreen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    object Orders : DelivererScreen(
        route = "deliverer_orders",
        label = "Orders",
        selectedIcon = Icons.Filled.DeliveryDining,
        unselectedIcon = Icons.Outlined.DeliveryDining,
    )

    object Profile : DelivererScreen(
        route = "deliverer_profile",
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.PersonOutline,
    )

    companion object {
        val bottomNavItems = listOf(Orders, Profile)
    }
}

// ─── Nav host ─────────────────────────────────────────────────────────────────

@Composable
fun DelivererNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                DelivererScreen.bottomNavItems.forEach { screen ->
                    val isSelected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon
                                else screen.unselectedIcon,
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
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "auth",
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) {
            composable("auth") {
                AuthScreen(
                    role = UserRole.SHOPPER,
                    onAuthSuccess = {
                        navController.navigate("browse") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }
            composable(DelivererScreen.Orders.route) {
                DelivererOrdersScreen()
            }
            composable(DelivererScreen.Profile.route) {
                // Reuses the shopper ProfileScreen — shows the deliverer's own profile
                // and any orders placed under their account (empty for a pure deliverer).
                ProfileScreen()
            }
        }
    }
}