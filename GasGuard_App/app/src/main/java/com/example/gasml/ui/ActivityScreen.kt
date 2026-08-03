package com.example.gasml.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gasml.model.Order
import com.example.gasml.ui.theme.*
import com.example.gasml.viewmodel.AuthViewModel
import com.example.gasml.viewmodel.OrderViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ActivityScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    orderViewModel: OrderViewModel
) {
    val orders by orderViewModel.orders.collectAsState()

    // UI FIXED: Removed internal Scaffold to prevent "Screens Mix" with MainScreen's global Scaffold
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
    ) {
        ActivityHeader()
        Spacer(modifier = Modifier.height(24.dp))
        
        if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recent activity", color = TextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(orders, key = { it.id }) { order ->
                    ActivityOrderCard(order)
                }
            }
        }
    }
}

@Composable
fun ActivityOrderCard(order: Order) {
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    // Safe date formatting using the helper method
    val dateString = try {
        sdf.format(Date(order.getEffectiveTimestampMillis()))
    } catch (e: Exception) {
        "Just now"
    }

    val (icon, tint) = when (order.status) {
        "Delivered" -> Icons.Default.CheckCircle to StatusGreen
        "Out for Delivery" -> Icons.Default.LocalShipping to SignalBlue
        "Pending" -> Icons.Default.History to TempOrange
        "Processing" -> Icons.Default.Autorenew to PrimaryGreen
        else -> Icons.Default.Receipt to PrimaryGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(tint.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.status,
                    style = Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
                Text(
                    // Safe price formatting using the helper method
                    text = "${order.cylinderType} • Rs. ${order.getEffectiveTotalPrice().toInt()}",
                    style = Typography.bodyMedium,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
            }
            Text(
                text = dateString,
                style = Typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Top)
            )
        }
    }
}

@Composable
fun ActivityHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(PrimaryGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Activity",
                style = Typography.titleLarge,
                fontSize = 22.sp
            )
            Text(
                text = "Your recent events",
                style = Typography.bodyMedium,
                color = TextSecondary.copy(alpha = 0.7f)
            )
        }
    }
}
