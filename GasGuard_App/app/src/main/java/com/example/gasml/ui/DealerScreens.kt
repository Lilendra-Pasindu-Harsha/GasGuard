package com.example.gasml.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.gasml.model.Order
import com.example.gasml.ui.theme.*
import com.example.gasml.viewmodel.AuthViewModel
import com.example.gasml.viewmodel.OrderViewModel
import com.example.gasml.viewmodel.InventoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DealerOrdersScreen(navController: NavController, orderViewModel: OrderViewModel = viewModel()) {
    val orders by orderViewModel.orders.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        orderViewModel.loadDealerOrders()
    }

    val activeOrders = orders.filter { it.status != "Delivered" }
    val completedOrders = orders.filter { it.status == "Delivered" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("Order Management", style = Typography.headlineMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = PrimaryGreen,
            divider = {},
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PrimaryGreen
                    )
                }
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Active (${activeOrders.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Completed", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val displayList = if (selectedTab == 0) activeOrders else completedOrders

        if (displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (selectedTab == 0) "No active orders" else "No completed orders", color = TextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayList) { order ->
                    DealerOrderCard(order) { newStatus ->
                        orderViewModel.updateStatus(order.id, newStatus)
                    }
                }
            }
        }
    }
}

@Composable
fun DealerOrderCard(order: Order, onStatusChange: (String) -> Unit) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    val dateString = sdf.format(Date(order.getEffectiveTimestampMillis()))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(order.userName, style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(order.cylinderType, style = Typography.labelSmall, color = TextSecondary)
                }
                Text("Rs. ${order.getEffectiveTotalPrice().toInt()}", style = Typography.bodyLarge, color = PrimaryGreen, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(order.address, style = Typography.labelSmall, color = TextSecondary.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            }
            
            if (order.mapLocationUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(order.mapLocationUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Map, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View on Map", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ", style = Typography.labelSmall, color = TextSecondary)
                val statusColor = when(order.status) {
                    "Delivered" -> StatusGreen
                    "Out for Delivery" -> SignalBlue
                    "Pending" -> TempOrange
                    else -> PrimaryGreen
                }
                Text(order.status, style = Typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
            }
            
            if (order.status != "Delivered") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (order.status == "Pending") {
                        StatusButton("Accept", PrimaryGreen, Modifier.weight(1f)) { onStatusChange("Processing") }
                    }
                    if (order.status == "Processing") {
                        StatusButton("Dispatch", SignalBlue, Modifier.weight(1f)) { onStatusChange("Out for Delivery") }
                    }
                    if (order.status == "Out for Delivery") {
                        StatusButton("Mark Delivered", StatusGreen, Modifier.weight(1f)) { onStatusChange("Delivered") }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DealerStockScreen(
    navController: NavController,
    inventoryViewModel: InventoryViewModel = viewModel()
) {
    val inventory by inventoryViewModel.inventory.collectAsState()

    val stockItems = listOf(
        Triple("Standard 5 kg", "standard_5kg", 142),
        Triple("Small 2.5 kg", "small_2_5kg", 45),
        Triple("Large 12.5 kg", "large_12_5kg", 12)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(20.dp)
    ) {
        Text("Inventory Management", style = Typography.headlineMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(24.dp))

        stockItems.forEach { (displayName, dbKey, defaultVal) ->
            val count = inventory[dbKey] ?: defaultVal
            StockItem(
                name = displayName,
                count = count,
                onIncrement = { inventoryViewModel.updateStock(dbKey, count + 1) },
                onDecrement = { inventoryViewModel.updateStock(dbKey, count - 1) },
                onUpdate = { newCount -> inventoryViewModel.updateStock(dbKey, newCount) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockItem(
    name: String,
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onUpdate: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(count.toString()) }

    LaunchedEffect(showDialog) {
        if (showDialog) {
            editValue = count.toString()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(PrimaryGreen.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Inventory, contentDescription = null, tint = PrimaryGreen)
            }
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "$count in stock",
                    color = if (count > 20) PrimaryGreen else Color.Red,
                    fontWeight = FontWeight.SemiBold,
                    style = Typography.labelMedium,
                    modifier = Modifier.clickable { showDialog = true }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier
                        .size(36.dp)
                        .background(CardBackground, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(36.dp)
                        .background(CardBackground, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Edit Stock: $name", style = Typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            editValue = newValue
                        }
                    },
                    label = { Text("Stock Quantity") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        focusedLabelColor = PrimaryGreen
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newCount = editValue.toIntOrNull() ?: count
                        onUpdate(newCount)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Save", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardBackground
        )
    }
}

@Composable
fun DealerFleetScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(20.dp)
    ) {
        Text("Fleet Tracking", style = Typography.headlineMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(24.dp))
        
        FleetDriverItem("Arjun", "Online", "2.1 km away")
        Spacer(modifier = Modifier.height(16.dp))
        FleetDriverItem("Sana", "Delivering", "0.6 km away")
    }
}

@Composable
fun FleetDriverItem(name: String, status: String, distance: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(SecondaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = SecondaryBlue)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(distance, style = Typography.labelSmall, color = TextSecondary)
            }
            Text(status, color = if(status == "Online") PrimaryGreen else SignalBlue, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DealerMeScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    orderViewModel: OrderViewModel = viewModel()
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }

    // Load dealer orders for the Sales Report calculations
    LaunchedEffect(Unit) {
        orderViewModel.loadDealerOrders()
    }

    val orders by orderViewModel.orders.collectAsState()
    val totalRevenue by orderViewModel.totalRevenue.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (selectedOption == null) {
            Box(modifier = Modifier.size(100.dp).background(PrimaryGreen, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(50.dp), tint = DarkBackground)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(authViewModel.user?.name ?: "Janaka Stores", style = Typography.headlineMedium)
            Text("ID: ${authViewModel.user?.uid?.takeLast(5) ?: "N/A"}", color = TextSecondary)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            ProfileOption("Business Profile", Icons.Default.Business) {
                selectedOption = "Business Profile"
            }
            ProfileOption("Sales Reports", Icons.Default.BarChart) {
                selectedOption = "Sales Reports"
            }
            ProfileOption("Settings", Icons.Default.Settings) {
                selectedOption = "Settings"
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { 
                    authViewModel.logout {
                        navController.navigate(Screen.Login.route) { 
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Logout", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        } else {
            // Header for Selected Option with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedOption = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryGreen)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(selectedOption!!, style = Typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            when (selectedOption) {
                "Business Profile" -> BusinessProfileDetailsCard()
                "Sales Reports" -> SalesReportsDetailsCard(orders, totalRevenue)
                "Settings" -> SettingsDetailsCard()
            }
        }
    }
}

@Composable
fun ProfileOption(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryGreen)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = Typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
fun BusinessProfileDetailsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Business Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Store, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Business Name", style = Typography.labelSmall, color = TextSecondary)
                    Text("Janaka stores", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Business Type
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("📍 Business Type", style = Typography.labelSmall, color = TextSecondary)
                    Text("LPG Gas Cylinder Sales & Home/Commercial Gas Supply", style = Typography.bodyMedium, color = TextPrimary)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Contact Details
            Text("📞 Contact Details", style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = PrimaryGreen)
            Spacer(modifier = Modifier.height(12.dp))
            
            ContactDetailItem(Icons.Default.Phone, "Phone", "+94 77 123 4567")
            Spacer(modifier = Modifier.height(8.dp))
            ContactDetailItem(Icons.Default.Chat, "WhatsApp", "+94 77 123 4567")
            Spacer(modifier = Modifier.height(8.dp))
            ContactDetailItem(Icons.Default.Email, "Email", "janakastores@gmail.com")
            Spacer(modifier = Modifier.height(8.dp))
            ContactDetailItem(Icons.Default.Home, "Address", "123 Main Street, Colombo")
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            
            // About Us
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("📝 About Us", style = Typography.labelSmall, color = TextSecondary)
                    Text(
                        "We provide safe, reliable, and affordable LPG gas cylinders for homes and businesses. Fast delivery, quality service, and customer satisfaction are our top priorities.",
                        style = Typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ContactDetailItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("$label: ", style = Typography.bodyMedium, color = TextSecondary)
        Text(value, style = Typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SalesReportsDetailsCard(orders: List<Order>, totalRevenue: Double) {
    val completedOrders = orders.filter { it.status == "Delivered" }
    val ordersDone = completedOrders.size
    val completionPercentage = if (orders.isNotEmpty()) {
        (completedOrders.size.toFloat() / orders.size * 100).toInt()
    } else {
        100
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Monthly Performance Summary", style = Typography.labelSmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Total Revenue card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryGreen.copy(0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MonetizationOn, null, tint = PrimaryGreen, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Total Revenue", style = Typography.labelSmall, color = TextSecondary)
                    Text("Rs. ${"%,.0f".format(totalRevenue)}", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Two columns for orders and completion rate
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Orders Done", style = Typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$ordersDone", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Completion", style = Typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$completionPercentage%", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Recent Activity", style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            
            if (completedOrders.isEmpty()) {
                Text("No recent activity", style = Typography.bodyMedium, color = TextSecondary)
            } else {
                val recentOrders = completedOrders.sortedByDescending { it.getEffectiveTimestampMillis() }.take(2)
                val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                
                recentOrders.forEachIndexed { index, order ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Refill Order #${order.id.takeLast(4)}", style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                            val dateString = sdf.format(Date(order.getEffectiveTimestampMillis()))
                            Text(dateString, style = Typography.labelSmall, color = TextSecondary)
                        }
                        Text("+ Rs. ${order.getEffectiveTotalPrice().toInt()}", color = PrimaryGreen, fontWeight = FontWeight.Bold, style = Typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDetailsCard() {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var autoAcceptEnabled by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("App Settings & Preferences", style = Typography.labelSmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Notification Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Push Notifications", style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("Get notified when new orders arrive", style = Typography.labelSmall, color = TextSecondary)
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen, checkedTrackColor = PrimaryGreen.copy(0.5f))
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Auto Accept Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Accept Orders", style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("Automatically accept incoming delivery requests", style = Typography.labelSmall, color = TextSecondary)
                }
                Switch(
                    checked = autoAcceptEnabled,
                    onCheckedChange = { autoAcceptEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen, checkedTrackColor = PrimaryGreen.copy(0.5f))
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Account settings list
            OutlinedButton(
                onClick = { /* Action */ },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Change Password", style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
