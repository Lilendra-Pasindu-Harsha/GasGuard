package com.example.gasml.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.gasml.model.Order
import com.example.gasml.ui.theme.*
import com.example.gasml.viewmodel.AuthViewModel
import com.example.gasml.viewmodel.OrderViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    orderViewModel: OrderViewModel
) {
    val scrollState = rememberScrollState()
    val user = authViewModel.user
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val cylinderTypes = listOf("Small 2.5 kg", "Standard 5 kg", "Large 12.5 kg")
    val prices = listOf(650.0, 1200.0, 2800.0)
    
    var expanded by remember { mutableStateOf(false) }
    var selectedTypeIndex by remember { mutableIntStateOf(1) }

    var quantity by remember { mutableIntStateOf(1) }
    var selectedPayment by remember { mutableIntStateOf(0) }
    var manualAddress by remember { mutableStateOf(user?.address ?: "") }
    
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }

    val settingResultRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fetchCurrentLocation(context) { lat, lon ->
                latitude = lat
                longitude = lon
                isFetchingLocation = false
                showMapPicker = true
            }
        } else {
            isFetchingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            checkAndFetchLocation(context, settingResultRequest, 
                onFetching = { isFetchingLocation = true },
                onLocationFound = { lat, lon ->
                    latitude = lat
                    longitude = lon
                    isFetchingLocation = false
                    showMapPicker = true
                },
                onFailure = { 
                    isFetchingLocation = false
                    showMapPicker = true
                }
            )
        } else {
            showMapPicker = true
        }
    }

    if (showMapPicker) {
        MapPickerDialog(
            initialLocation = if (latitude != null && longitude != null) LatLng(latitude!!, longitude!!) else LatLng(6.9271, 79.8612),
            onLocationSelected = { latLng ->
                latitude = latLng.latitude
                longitude = latLng.longitude
                showMapPicker = false
                
                coroutineScope.launch {
                    val addressResult = withContext(Dispatchers.IO) {
                        reverseGeocode(context, latLng.latitude, latLng.longitude)
                    }
                    manualAddress = addressResult
                }
            },
            onDismiss = { showMapPicker = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            OrderHeader()
            Spacer(modifier = Modifier.height(24.dp))
            
            SectionHeader("SELECT CYLINDER TYPE")
            Spacer(modifier = Modifier.height(12.dp))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = cylinderTypes[selectedTypeIndex],
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Gas Type", color = TextSecondary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedIndicatorColor = PrimaryGreen,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(CardBackground)
                ) {
                    cylinderTypes.forEachIndexed { index, type ->
                        DropdownMenuItem(
                            text = { 
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(type, color = TextPrimary)
                                    Text("Rs. ${prices[index].toInt()}", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = {
                                selectedTypeIndex = index
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("QUANTITY")
            Spacer(modifier = Modifier.height(12.dp))
            QuantitySelector(quantity) { if (it > 0) quantity = it }
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("DELIVERY ADDRESS")
            Spacer(modifier = Modifier.height(12.dp))
            AddressInputCard(
                address = manualAddress, 
                onAddressChange = { manualAddress = it },
                hasLocation = latitude != null,
                isFetching = isFetchingLocation,
                onFetchLocation = {
                    if (hasLocationPermission(context)) {
                        checkAndFetchLocation(context, settingResultRequest,
                            onFetching = { isFetchingLocation = true },
                            onLocationFound = { lat, lon ->
                                latitude = lat
                                longitude = lon
                                isFetchingLocation = false
                                showMapPicker = true
                            },
                            onFailure = { 
                                isFetchingLocation = false
                                showMapPicker = true
                            }
                        )
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("PAYMENT METHOD")
            Spacer(modifier = Modifier.height(12.dp))
            PaymentSelector(selectedPayment) { selectedPayment = it }
            
            Spacer(modifier = Modifier.height(140.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp) 
                .padding(horizontal = 20.dp)
        ) {
            val totalPrice = prices[selectedTypeIndex] * quantity
            PlaceOrderButton(
                totalPrice = "Rs. ${totalPrice.toInt()}",
                isEnabled = !orderViewModel.isPlacingOrder && manualAddress.length > 5,
                isLoading = orderViewModel.isPlacingOrder
            ) {
                val mapUrl = if (latitude != null && longitude != null) {
                    "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
                } else ""

                val order = Order(
                    userId = user?.uid ?: "",
                    userName = user?.name ?: "Unknown",
                    cylinderType = cylinderTypes[selectedTypeIndex],
                    quantity = quantity.toLong(),
                    totalPrice = totalPrice,
                    status = "Pending",
                    timestamp = System.currentTimeMillis(),
                    address = manualAddress,
                    paymentMethod = if (selectedPayment == 0) "Online / Card" else "Cash on delivery",
                    latitude = latitude,
                    longitude = longitude,
                    mapLocationUrl = mapUrl
                )
                orderViewModel.placeOrder(order, 
                    onSuccess = { 
                        navController.navigate(Screen.Activity.route) {
                            popUpTo(Screen.Order.route) { inclusive = true }
                        }
                    },
                    onError = { /* Error handled in VM/UI */ }
                )
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
           ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun checkAndFetchLocation(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onFetching: () -> Unit,
    onLocationFound: (Double, Double) -> Unit,
    onFailure: () -> Unit
) {
    onFetching()
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
    val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
    val client: SettingsClient = LocationServices.getSettingsClient(context)
    
    client.checkLocationSettings(builder.build())
        .addOnSuccessListener {
            fetchCurrentLocation(context, onLocationFound)
        }
        .addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                    launcher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    onFailure()
                }
            } else {
                onFailure()
            }
        }
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(context: Context, onLocationFound: (Double, Double) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            onLocationFound(location.latitude, location.longitude)
        } else {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMaxUpdates(1)
                .build()
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let {
                            onLocationFound(it.latitude, it.longitude)
                        }
                    }
                },
                context.mainLooper
            )
        }
    }
}

/**
 * Reverse geocodes a lat/lng to a human-readable address.
 * Tries the device Geocoder first, then falls back to the Google Maps Geocoding HTTP API.
 */
private fun reverseGeocode(context: Context, lat: Double, lng: Double): String {
    // Attempt 1: Use the device's built-in Geocoder
    try {
        if (Geocoder.isPresent()) {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0].getAddressLine(0)
                if (!addr.isNullOrBlank()) {
                    Log.d("OrderScreen", "Device Geocoder succeeded: $addr")
                    return addr
                }
            }
        }
    } catch (e: Exception) {
        Log.w("OrderScreen", "Device Geocoder failed, trying HTTP API fallback", e)
    }

    // Attempt 2: Use the Google Maps Geocoding HTTP API as a fallback
    try {
        val apiKey = context.packageManager
            .getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        
        val url = URL("https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val status = json.optString("status")
            if (status == "OK") {
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val addr = results.getJSONObject(0).optString("formatted_address", "")
                    if (addr.isNotBlank()) {
                        Log.d("OrderScreen", "HTTP Geocoding API succeeded: $addr")
                        return addr
                    }
                }
            }
        }
        connection.disconnect()
    } catch (e: Exception) {
        Log.w("OrderScreen", "HTTP Geocoding API also failed", e)
    }

    // Fallback: Return raw coordinates
    return "Lat: $lat, Lon: $lng"
}

@Composable
fun MapPickerDialog(
    initialLocation: LatLng,
    onLocationSelected: (LatLng) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLocation by remember { mutableStateOf(initialLocation) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLocation, 15f)
    }
    val markerState = rememberMarkerState(position = initialLocation)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBackground)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { latLng ->
                            selectedLocation = latLng
                            markerState.position = latLng
                        }
                    ) {
                        Marker(
                            state = markerState,
                            title = "Delivery Location"
                        )
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onLocationSelected(selectedLocation) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("Confirm Location", color = DarkBackground)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressInputCard(
    address: String,
    onAddressChange: (String) -> Unit,
    hasLocation: Boolean,
    isFetching: Boolean,
    onFetchLocation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Delivery Address", color = TextPrimary, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = onFetchLocation,
                    enabled = !isFetching
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryGreen)
                    } else {
                        Icon(
                            if (hasLocation) Icons.Default.MyLocation else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (hasLocation) "Update Location" else "Get Location", color = PrimaryGreen, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your full address", color = TextSecondary.copy(alpha = 0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceColor.copy(alpha = 0.3f),
                    unfocusedContainerColor = SurfaceColor.copy(alpha = 0.3f),
                    focusedIndicatorColor = PrimaryGreen,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                maxLines = 3
            )
        }
    }
}

@Composable
fun OrderHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp).background(PrimaryGreen, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ShoppingBag, null, tint = DarkBackground, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Refill Order", style = Typography.titleLarge, fontSize = 22.sp)
            Text("Sri Lankan standard delivery", style = Typography.bodyMedium, color = TextSecondary.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = Typography.labelSmall,
        color = TextSecondary.copy(alpha = 0.6f),
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun QuantitySelector(quantity: Int, onQuantityChange: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onQuantityChange(quantity - 1) },
                modifier = Modifier.size(56.dp).background(SurfaceColor, CircleShape)
            ) { Icon(Icons.Default.Remove, null, tint = TextPrimary) }
            
            Text(quantity.toString(), style = Typography.headlineMedium, fontWeight = FontWeight.Bold)
            
            IconButton(
                onClick = { onQuantityChange(quantity + 1) },
                modifier = Modifier.size(56.dp).background(PrimaryGreen, CircleShape)
            ) { Icon(Icons.Default.Add, null, tint = DarkBackground) }
        }
    }
}

@Composable
fun PaymentSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PaymentOption(Icons.Default.AccountBalance, "Online", selected == 0, Modifier.weight(1f)) { onSelect(0) }
        PaymentOption(Icons.Default.Payments, "Cash", selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
fun PaymentOption(icon: ImageVector, label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) PrimaryGreen else SurfaceColor),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color.Transparent else CardBackground)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (isSelected) PrimaryGreen else TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = if (isSelected) TextPrimary else TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PlaceOrderButton(totalPrice: String, isEnabled: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, disabledContainerColor = PrimaryGreen.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(32.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(24.dp))
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Place Order ($totalPrice)", color = DarkBackground, style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ArrowForward, null, tint = DarkBackground)
            }
        }
    }
}
