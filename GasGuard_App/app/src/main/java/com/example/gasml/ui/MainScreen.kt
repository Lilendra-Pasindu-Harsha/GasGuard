package com.example.gasml.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gasml.ui.theme.DarkBackground
import com.example.gasml.ui.theme.PrimaryGreen
import com.example.gasml.ui.theme.TextSecondary
import com.example.gasml.ui.theme.Typography
import com.example.gasml.viewmodel.*

sealed class Screen(val route: String, val label: String = "", val icon: ImageVector? = null, val selectedIcon: ImageVector? = null) {
    object Login : Screen("login")
    object Register : Screen("register")
    
    object Home : Screen("home", "Home", Icons.Default.Home, Icons.Default.Home)
    object Stats : Screen("stats", "Stats", Icons.Outlined.BarChart, Icons.Default.BarChart)
    object Order : Screen("order", "Order", Icons.Outlined.ShoppingBag, Icons.Default.ShoppingBag)
    object Activity : Screen("activity", "Activity", Icons.Outlined.Notifications, Icons.Default.Notifications)
    object Chat : Screen("chat", "Chat", Icons.Outlined.ChatBubbleOutline, Icons.Default.ChatBubbleOutline)
    
    object DealerHome : Screen("dealer_home", "Home", Icons.Default.GridView, Icons.Default.GridView)
    object DealerOrders : Screen("dealer_orders", "Orders", Icons.Outlined.ShoppingBag, Icons.Default.ShoppingBag)
    object DealerStock : Screen("dealer_stock", "Stock", Icons.Outlined.Inventory2, Icons.Default.Inventory2)
    object DealerChat : Screen("dealer_chat", "Chat", Icons.Outlined.ChatBubbleOutline, Icons.Default.ChatBubbleOutline)
    object DealerMe : Screen("dealer_me", "Me", Icons.Outlined.Person, Icons.Default.Person)
    object DealerFleet : Screen("dealer_fleet", "Fleet", Icons.Outlined.LocalShipping, Icons.Default.LocalShipping)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val orderViewModel: OrderViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val gasStatsViewModel: GasStatsViewModel = viewModel()

    val user = authViewModel.user
    val isSessionChecked = authViewModel.isSessionChecked

    LaunchedEffect(user?.uid, user?.role) {
        if (user != null) {
            if (user.role == "Dealer") {
                orderViewModel.loadDealerOrders()
            } else {
                orderViewModel.loadCustomerOrders(user.uid)
            }
        }
    }

    if (!isSessionChecked) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryGreen)
        }
        return
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = { AppBottomNavBar(navController, authViewModel) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (user != null) {
                if (user.role == "Dealer") Screen.DealerHome.route else Screen.Home.route
            } else Screen.Login.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Login.route) { LoginScreen(navController, authViewModel) }
            composable(Screen.Register.route) { RegisterScreen(navController, authViewModel) }
            
            // Customer
            composable(Screen.Home.route) { DashboardScreen(navController, authViewModel, gasStatsViewModel, orderViewModel) }
            composable(Screen.Stats.route) { StatsScreen(navController, authViewModel, gasStatsViewModel) }
            composable(Screen.Order.route) { OrderScreen(navController, authViewModel, orderViewModel) }
            composable(Screen.Activity.route) { ActivityScreen(navController, authViewModel, orderViewModel) }
            composable(Screen.Chat.route) { ChatScreen(navController, authViewModel, chatViewModel) }
            
            // Dealer
            composable(Screen.DealerHome.route) { DealerDashboardScreen(navController, authViewModel, orderViewModel) }
            composable(Screen.DealerOrders.route) { DealerOrdersScreen(navController, orderViewModel) }
            composable(Screen.DealerStock.route) { DealerStockScreen(navController) }
            composable(Screen.DealerChat.route) { ChatScreen(navController, authViewModel, chatViewModel) }
            composable(Screen.DealerMe.route) { DealerMeScreen(navController, authViewModel, orderViewModel) }
            composable(Screen.DealerFleet.route) { DealerFleetScreen(navController) }

            composable("dealer_selection") { DealerSelectionScreen(navController, chatViewModel) }
            composable(
                route = "chat_detail/{otherUserId}/{otherUserName}",
                arguments = listOf(
                    navArgument("otherUserId") { type = NavType.StringType },
                    navArgument("otherUserName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""
                val otherUserName = backStackEntry.arguments?.getString("otherUserName") ?: ""
                ChatDetailScreen(navController, authViewModel, chatViewModel, otherUserId, otherUserName)
            }
        }
    }
}

@Composable
fun AppBottomNavBar(navController: NavController, authViewModel: AuthViewModel) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: return
    val user = authViewModel.user

    if (currentRoute == Screen.Login.route || currentRoute == Screen.Register.route || 
        currentRoute.startsWith("chat_detail") || currentRoute == "dealer_selection") return

    // Screen Mix Fix: Use role-based logic
    val items = if (user?.role == "Dealer") {
        listOf(Screen.DealerHome, Screen.DealerOrders, Screen.DealerStock, Screen.DealerChat, Screen.DealerMe)
    } else {
        listOf(Screen.Home, Screen.Stats, Screen.Order, Screen.Activity, Screen.Chat)
    }

    Surface(
        color = DarkBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val isSelected = currentRoute == screen.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).noRippleClickable {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                ) {
                    if (isSelected && screen.selectedIcon != null) {
                        Box(modifier = Modifier.size(42.dp).background(PrimaryGreen, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(screen.selectedIcon, null, tint = DarkBackground, modifier = Modifier.size(22.dp))
                        }
                    } else if (screen.icon != null) {
                        Icon(screen.icon, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(screen.label, style = Typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
