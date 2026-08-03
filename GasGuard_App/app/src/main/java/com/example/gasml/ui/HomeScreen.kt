package com.example.gasml.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gasml.model.GasStats
import com.example.gasml.model.DeviceStatus
import com.example.gasml.ui.theme.*
import com.example.gasml.util.NetworkObserver
import com.example.gasml.viewmodel.AuthViewModel
import com.example.gasml.viewmodel.GasStatsViewModel
import com.example.gasml.viewmodel.OrderViewModel

@Composable
fun DashboardScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    statsViewModel: GasStatsViewModel,
    orderViewModel: OrderViewModel
) {
    val scrollState = rememberScrollState()
    val user = authViewModel.user
    val stats by statsViewModel.stats.collectAsState()
    val deviceStatus by statsViewModel.deviceStatus.collectAsState()
    val orders by orderViewModel.orders.collectAsState()
    val networkStatus by statsViewModel.networkStatus.collectAsState()
    val isLoading by statsViewModel.isLoading.collectAsState()

    val activeOrder = orders.firstOrNull { it.status != "Delivered" && it.status != "Cancelled" }

    LaunchedEffect(user?.unitId) {
        if (user?.unitId != null) {
            statsViewModel.loadStats(user.unitId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (isLoading && stats == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        } else if (user?.unitId == null) {
            NoUnitBoundState(navController)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                HeaderSection(
                    userName = user.name,
                    currentDate = statsViewModel.getCurrentTime(),
                    networkStatus = networkStatus
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                if (activeOrder != null) {
                    ActiveOrderBanner(activeOrder) {
                        navController.navigate(Screen.Activity.route)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                CylinderCard(stats, deviceStatus, statsViewModel, navController)
                Spacer(modifier = Modifier.height(24.dp))
                
                StatusBanner(stats, deviceStatus, statsViewModel, user.unitId)
                Spacer(modifier = Modifier.height(16.dp))

                LeakSimulationCard(stats, statsViewModel, user.unitId)
                Spacer(modifier = Modifier.height(24.dp))

                // ESP32 IoT Control Panel hidden per user request
                // IotControlPanelCard(deviceStatus, stats, statsViewModel)
                // Spacer(modifier = Modifier.height(24.dp))

                SensorGridFlow(stats, deviceStatus, statsViewModel)
                Spacer(modifier = Modifier.height(24.dp))

                UsageGraphCard(stats, deviceStatus)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ActiveOrderBanner(order: com.example.gasml.model.Order, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalShipping, null, tint = PrimaryGreen)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Ongoing Delivery", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Status: ${order.status}", style = Typography.labelSmall, color = PrimaryGreen)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
        }
    }
}

@Composable
fun LeakSimulationCard(stats: GasStats?, viewModel: GasStatsViewModel, unitId: String?) {
    var isLeak by remember { mutableStateOf(stats?.leakDetected == true) }

    LaunchedEffect(stats?.leakDetected) {
        isLeak = stats?.leakDetected == true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isLeak) Color.Red.copy(alpha = 0.1f) else SurfaceColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Simulate Leak Alert (Software)", style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (isLeak) "Simulation active. Stop to clear alert." else "Tap to simulate a gas leak in backend.",
                    style = Typography.labelSmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = isLeak,
                onCheckedChange = { checked ->
                    isLeak = checked
                    viewModel.toggleLeakSimulation(unitId, checked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Red,
                    checkedTrackColor = Color.Red.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun IotControlPanelCard(deviceStatus: DeviceStatus?, stats: GasStats?, viewModel: GasStatsViewModel) {
    if (deviceStatus == null) return
    
    val deviceId = deviceStatus.deviceId.ifBlank { stats?.deviceId ?: "gasguard-esp32-01" }
    val isValveOpen = deviceStatus.valveState.equals("OPEN", ignoreCase = true)
    val isAlarmActive = deviceStatus.alarmActive || deviceStatus.gasDetected

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsRemote, null, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("ESP32 IoT Control Panel", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))

            // Valve Control Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Solenoid Gas Valve", style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isValveOpen) "State: OPEN (Flow Active)" else "State: SHUT / CLOSED",
                        style = Typography.labelSmall,
                        color = if (isValveOpen) StatusGreen else Color.Red
                    )
                }
                
                Button(
                    onClick = {
                        val targetState = if (isValveOpen) "CLOSED" else "OPEN"
                        viewModel.toggleValveState(deviceId, targetState)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isValveOpen) Color.Red.copy(alpha = 0.15f) else PrimaryGreen.copy(alpha = 0.15f),
                        contentColor = if (isValveOpen) Color.Red else PrimaryGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isValveOpen) Color.Red.copy(alpha = 0.4f) else PrimaryGreen.copy(alpha = 0.4f)),
                    enabled = !isAlarmActive || isValveOpen // Can always close, but cannot open during active alarm
                ) {
                    Text(if (isValveOpen) "Close Valve" else "Open Valve", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            
            if (isAlarmActive && !isValveOpen) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "⚠️ Valve locked closed. Cannot open while gas level is critical.",
                    color = Color.Red,
                    style = Typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(20.dp))

            // Threshold Control Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Gas Leak Threshold", style = Typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Current Limit: ${deviceStatus.gasThreshold} (MQ-5 analog raw)", style = Typography.labelSmall, color = TextSecondary)
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val newTh = (deviceStatus.gasThreshold - 50).coerceAtLeast(200L)
                                viewModel.updateGasThreshold(deviceId, newTh)
                            },
                            modifier = Modifier.size(36.dp).background(CardBackground, CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }
                        
                        Text(
                            text = deviceStatus.gasThreshold.toString(),
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 36.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        IconButton(
                            onClick = {
                                val newTh = (deviceStatus.gasThreshold + 50).coerceAtLeast(200L)
                                viewModel.updateGasThreshold(deviceId, newTh)
                            },
                            modifier = Modifier.size(36.dp).background(CardBackground, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoUnitBoundState(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SettingsRemote, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("No hardware unit bound", style = Typography.headlineSmall, color = TextPrimary)
        Text(
            "Please bind your 3-digit unit code in settings or during registration to see live data.",
            style = Typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
fun CylinderCard(stats: GasStats?, deviceStatus: DeviceStatus?, viewModel: GasStatsViewModel, navController: NavController) {
    // Display the cylinderWeightKg directly from ESP32 — this is the net weight
    // on the scale (platform already tared out by firmware in SensorTest.ino)
    val weight = if (deviceStatus != null) {
        deviceStatus.cylinderWeightKg
    } else {
        stats?.currentWeight ?: 0.0
    }
    val clampedWeight = weight.coerceIn(0.0, 5.0)
    val percent = ((clampedWeight / 5.0) * 100).toInt().coerceIn(0, 100)
    val daysRemaining by viewModel.daysRemaining.collectAsState()
    val refillDate by viewModel.refillDate.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF00695C), Color(0xFF003D33))
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CylinderProgress(percent)
                    Spacer(modifier = Modifier.width(28.dp))
                    Column {
                        Text(
                            text = "LIVE UNIT: ${deviceStatus?.deviceId?.uppercase() ?: stats?.unitId ?: "..."}",
                            style = Typography.labelSmall,
                            color = TextSecondary.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.2f".format(clampedWeight),
                                style = Typography.headlineMedium,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " kg / 5 kg",
                                style = Typography.bodyLarge,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ElectricBolt, null, tint = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    Text(
                        text = " ~$daysRemaining days left • Predicted $refillDate",
                        style = Typography.bodyMedium,
                        fontSize = 13.sp,
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { navController.navigate(Screen.Order.route) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(27.dp)
                ) {
                    Text("Order refill →", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderSection(userName: String, currentDate: String, networkStatus: NetworkObserver.Status) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp).background(PrimaryGreen, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LocalFireDepartment, null, tint = DarkBackground, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Hi, $userName 👋", style = Typography.titleLarge, fontSize = 22.sp)
            Text(text = currentDate, style = Typography.bodyMedium, color = TextSecondary.copy(alpha = 0.7f) )
        }
        
        val networkColor = when(networkStatus) {
            NetworkObserver.Status.Available -> StatusGreen
            else -> Color.Red
        }
        
        Box(modifier = Modifier.size(44.dp).background(SurfaceColor, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (networkStatus == NetworkObserver.Status.Available) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = networkColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CylinderProgress(percent: Int) {
    Box(
        modifier = Modifier.size(width = 70.dp, height = 100.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight((percent / 100f).coerceIn(0f, 1f))
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
        )
        Text(text = "$percent%", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun StatusBanner(stats: GasStats?, deviceStatus: DeviceStatus?, viewModel: GasStatsViewModel, unitId: String?) {
    val isSimulationActive = stats?.leakDetected == true
    val isHardwareLeak = deviceStatus != null && (deviceStatus.alarmActive || deviceStatus.gasDetected)
    val isSafe = !isSimulationActive && !isHardwareLeak
    
    val statusText = if (deviceStatus == null && stats == null) {
        "Connecting..."
    } else if (isSafe) {
        "Status: ${stats?.systemStatus ?: "NORMAL"}"
    } else {
        "ESP32 LEAK DETECTED!"
    }
    
    val statusColor = if (deviceStatus == null && stats == null) TextSecondary else if (isSafe) StatusGreen else Color.Red

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF081414))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSafe) Icons.Outlined.Shield else Icons.Default.Warning,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = Typography.bodyLarge.copy(color = statusColor, fontWeight = FontWeight.Bold)
                    )
                    
                    val timeVal = if (deviceStatus != null) "ESP32 Live Connection" else stats?.timestamp?.let { viewModel.formatESP32Timestamp(it) } ?: "..."
                    Text(
                        text = "Sync: $timeVal",
                        style = Typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                }
            }
            
            if (!isSafe) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.acknowledgeLeak(unitId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Acknowledge & Reset System", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SensorGridFlow(stats: GasStats?, deviceStatus: DeviceStatus?, viewModel: GasStatsViewModel) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 2
    ) {
        val itemModifier = Modifier.weight(1f)
        
        val gasVal = if (deviceStatus != null) {
            "${deviceStatus.gasLevel} MQ-5"
        } else {
            "${stats?.leakPercentage?.toInt()?.coerceIn(0, 100) ?: 0}%"
        }
        val isLeak = (stats?.leakDetected == true) || 
                     (deviceStatus != null && (deviceStatus.alarmActive || deviceStatus.gasDetected))
        
        val valveVal = if (deviceStatus != null) {
            deviceStatus.valveState
        } else {
            if (stats?.valveClosed == true) "CLOSED" else "OPEN"
        }
        val isValveClosed = valveVal.equals("CLOSED", ignoreCase = true)

        val tempVal = if (deviceStatus != null) {
            "${deviceStatus.temperatureC}°C"
        } else {
            "${stats?.temperature ?: 0.0}°C"
        }

        val humidityVal = if (deviceStatus != null) {
            "${deviceStatus.humidityPercent.toInt()}%"
        } else {
            "ONLINE"
        }

        SensorTile(
            icon = Icons.Default.Adjust, 
            label = "Gas Level", 
            value = gasVal, 
            iconColor = if (isLeak) Color.Red else PrimaryGreen, 
            modifier = itemModifier
        )
        SensorTile(
            icon = Icons.Default.Opacity, 
            label = "Valve State", 
            value = valveVal, 
            iconColor = if (isValveClosed) Color.Red else SecondaryBlue, 
            modifier = itemModifier
        )
        SensorTile(
            icon = Icons.Default.DeviceThermostat, 
            label = "Temperature", 
            value = tempVal, 
            iconColor = TempOrange, 
            modifier = itemModifier
        )
        SensorTile(
            icon = if (deviceStatus != null) Icons.Default.WaterDrop else Icons.Default.FlashOn, 
            label = if (deviceStatus != null) "Humidity" else "System", 
            value = humidityVal, 
            iconColor = PrimaryGreen, 
            modifier = itemModifier
        )
    }
}

@Composable
fun SensorTile(icon: ImageVector, label: String, value: String, iconColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(label, style = Typography.labelSmall, color = TextSecondary.copy(alpha = 0.7f))
            Text(value, style = Typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun UsageGraphCard(stats: GasStats?, deviceStatus: DeviceStatus?) {
    var selectedFilter by remember { mutableStateOf("Weekly") }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Gas Weight History", style = Typography.labelSmall, color = TextSecondary.copy(alpha = 0.6f))
                    Text("Consumption (kg)", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                GraphFilterChips(
                    options = listOf("Weekly", "Monthly"),
                    selected = selectedFilter,
                    onSelected = { selectedFilter = it }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Compute data and labels based on selected filter
            val allEntries = if (!stats?.history.isNullOrEmpty()) {
                stats!!.history.entries.sortedBy { it.key }
            } else {
                emptyList()
            }

            val historyData: List<Double>
            val dayLabels: List<String>

            if (allEntries.isNotEmpty()) {
                val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                when (selectedFilter) {
                    "Monthly" -> {
                        // Show all available history entries (up to 30)
                        val entries = allEntries.takeLast(30)
                        historyData = entries.map { entry ->
                            when (val v = entry.value) {
                                is Double -> v; is Long -> v.toDouble(); is Number -> v.toDouble()
                                is String -> v.toDoubleOrNull() ?: 0.0; else -> 0.0
                            }
                        }
                        val labelFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
                        dayLabels = entries.map {
                            try { labelFormat.format(inputFormat.parse(it.key)!!) }
                            catch (e: Exception) { it.key.takeLast(5) }
                        }
                    }
                    else -> {
                        // Weekly: last 7 entries
                        val entries = allEntries.takeLast(7)
                        historyData = entries.map { entry ->
                            when (val v = entry.value) {
                                is Double -> v; is Long -> v.toDouble(); is Number -> v.toDouble()
                                is String -> v.toDoubleOrNull() ?: 0.0; else -> 0.0
                            }
                        }
                        val dateFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                        dayLabels = entries.map {
                            try { dateFormat.format(inputFormat.parse(it.key)!!) }
                            catch (e: Exception) { it.key.takeLast(2) }
                        }
                    }
                }
            } else {
                // Fallback: generate from current weight
                val w = deviceStatus?.cylinderWeightKg ?: stats?.currentWeight ?: 4.5
                historyData = (6 downTo 0).map { i -> (w + i * 0.12).coerceAtMost(5.0) }
                dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            }

            UsageLineChart(historyData, dayLabels)
        }
    }
}

@Composable
fun UsageLineChart(data: List<Double>, dayLabels: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) {
    val textMeasurer = rememberTextMeasurer()
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            if (data.isNotEmpty()) {
                val path = Path()
                val xStep = size.width / (data.size - 1).coerceAtLeast(1)
                // Scale for gas weight range (0-5 kg) with some padding
                val maxVal = (data.maxOrNull() ?: 5.0).coerceAtLeast(1.0) * 1.15
                val minVal = ((data.minOrNull() ?: 0.0) * 0.85).coerceAtLeast(0.0)
                
                val paddingBottom = 15.dp.toPx()
                val paddingTop = 25.dp.toPx()
                val usableHeight = size.height - paddingTop - paddingBottom
                
                // Draw gradient fill
                val fillPath = Path()
                data.forEachIndexed { index, value ->
                    val x = index * xStep
                    val y = paddingTop + usableHeight - (((value - minVal) / (maxVal - minVal)) * usableHeight).toFloat()
                    if (index == 0) { path.moveTo(x, y); fillPath.moveTo(x, y) }
                    else { path.lineTo(x, y); fillPath.lineTo(x, y) }
                }
                // Close fill path
                fillPath.lineTo((data.size - 1) * xStep, paddingTop + usableHeight)
                fillPath.lineTo(0f, paddingTop + usableHeight)
                fillPath.close()
                
                drawPath(path = fillPath, brush = Brush.verticalGradient(
                    colors = listOf(PrimaryGreen.copy(alpha = 0.3f), PrimaryGreen.copy(alpha = 0.0f))
                ))
                drawPath(path = path, color = PrimaryGreen, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                data.forEachIndexed { index, value ->
                    val x = index * xStep
                    val y = paddingTop + usableHeight - (((value - minVal) / (maxVal - minVal)) * usableHeight).toFloat()
                    
                    drawCircle(color = PrimaryGreen, radius = 4.dp.toPx(), center = Offset(x, y))

                    val textLayoutResult = textMeasurer.measure(
                        text = "%.2f".format(value),
                        style = TextStyle(color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                    val textWidth = textLayoutResult.size.width
                    val textHeight = textLayoutResult.size.height
                    val labelX = when (index) {
                        0 -> x
                        data.size - 1 -> x - textWidth
                        else -> x - textWidth / 2
                    }
                    drawText(textLayoutResult = textLayoutResult, topLeft = Offset(labelX, y - textHeight - 4.dp.toPx()))
                }
            }
        }
        // Day labels row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayLabels.forEach { day ->
                Text(day, style = Typography.labelSmall, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun GraphFilterChips(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) PrimaryGreen else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelected(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = Typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) DarkBackground else TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
