package com.example.gasml.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gasml.model.Order
import com.example.gasml.ui.theme.*
import com.example.gasml.viewmodel.AuthViewModel
import com.example.gasml.viewmodel.OrderViewModel

@Composable
fun DealerDashboardScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    orderViewModel: OrderViewModel
) {
    val scrollState = rememberScrollState()
    val orders by orderViewModel.orders.collectAsState()

    LaunchedEffect(Unit) {
        orderViewModel.loadDealerOrders()
    }

    // PRODUCTION OPTIMIZATION: Recalculate only when 'orders' changes
    val activeOrdersCount by remember(orders) {
        derivedStateOf { orders.count { it.status != "Delivered" && it.status != "Cancelled" } }
    }
    
    val totalRevenue by remember(orders) {
        derivedStateOf { 
            orders.filter { it.status == "Delivered" }
                .fold(0.0) { acc, order -> acc + order.getEffectiveTotalPrice() }
        }
    }

    // UI FIXED: Removed internal Scaffold to prevent "Screens Mix" with MainScreen's global Scaffold
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        DealerHeader(authViewModel.user?.name ?: "Dealer")
        
        Spacer(modifier = Modifier.height(24.dp))
        RevenueCard(totalRevenue)
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Business Overview", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OverviewTile("Active Orders", activeOrdersCount.toString(), PrimaryGreen, Modifier.weight(1f))
            OverviewTile("Low Stock", "5kg", Color(0xFFFF5252), Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Quick Actions", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        QuickActionItem("View All Orders", Icons.Default.Receipt, PrimaryGreen) {
            navController.navigate(Screen.DealerOrders.route)
        }
        QuickActionItem("Manage Inventory", Icons.Default.Inventory, SignalBlue) {
            navController.navigate(Screen.DealerStock.route)
        }
        QuickActionItem("Fleet Tracking", Icons.Default.LocalShipping, SecondaryBlue) {
            navController.navigate(Screen.DealerFleet.route)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DealerHeader(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(52.dp).background(PrimaryGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Store, null, tint = DarkBackground, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text("Business Dashboard", style = Typography.bodyMedium, color = TextSecondary)
            Text(name, style = Typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RevenueCard(amount: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF004D40), Color(0xFF00796B))))
                .padding(24.dp)
        ) {
            Column {
                Text("Total Revenue (Delivered)", color = Color.White.copy(alpha = 0.7f), style = Typography.labelSmall)
                Text("Rs. ${"%,.2f".format(amount)}", color = Color.White, style = Typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Synced in real-time", color = PrimaryGreen, style = Typography.labelSmall)
            }
        }
    }
}

@Composable
fun OverviewTile(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(label, style = Typography.labelSmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = Typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickActionItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(color.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, modifier = Modifier.weight(1f), style = Typography.bodyLarge)
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
        }
    }
}
