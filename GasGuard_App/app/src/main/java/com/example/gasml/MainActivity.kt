package com.example.gasml

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gasml.service.GasLeakService
import com.example.gasml.ui.MainScreen
import com.example.gasml.ui.theme.GasMLTheme
import com.example.gasml.ui.theme.CardBackground
import com.example.gasml.ui.theme.TextPrimary
import com.example.gasml.viewmodel.AuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gasml.util.NotificationHelper

class MainActivity : ComponentActivity() {
    
    // Use a state to track the intent reactively
    private var activityIntent by mutableStateOf<Intent?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Update the reactive intent state
        activityIntent = intent

        // Configure activity to show over lockscreen for emergency alerts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        checkNotificationPermission()

        setContent {
            GasMLTheme {
                val authViewModel: AuthViewModel = viewModel()
                val user = authViewModel.user
                
                // Manage the background service lifecycle
                LaunchedEffect(user) {
                    if (user != null) {
                        val serviceIntent = Intent(this@MainActivity, GasLeakService::class.java).apply {
                            putExtra("UNIT_ID", user.unitId)
                            putExtra("USER_ID", user.uid)
                            putExtra("USER_ROLE", user.role)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    } else {
                        // Stop the service when user is null (logged out)
                        val serviceIntent = Intent(this@MainActivity, GasLeakService::class.java)
                        stopService(serviceIntent)
                    }
                }

                // State to control emergency dialog display
                var showLeakDialog by remember { mutableStateOf(false) }

                // Reactively handle incoming leak alerts to clear notifications and show dialog
                LaunchedEffect(activityIntent) {
                    val current = activityIntent
                    if (current?.getBooleanExtra("OPEN_LEAK_DIALOG", false) == true) {
                        showLeakDialog = true
                        NotificationHelper(this@MainActivity).clearAlert()
                    }
                }

                if (showLeakDialog) {
                    AlertDialog(
                        onDismissRequest = { showLeakDialog = false },
                        title = { Text("🚨 EMERGENCY ALERT", color = Color.Red, fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "CRITICAL GAS LEAK DETECTED!\n\n" +
                                "Please take immediate action:\n" +
                                "1. Open all doors and windows.\n" +
                                "2. Do NOT turn on or off any electrical switches or appliances.\n" +
                                "3. Evacuate the kitchen or house immediately.\n" +
                                "4. Close the main regulator valve if safe to do so.",
                                color = TextPrimary
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { showLeakDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Acknowledge safety", color = Color.White)
                            }
                        },
                        containerColor = CardBackground,
                        shape = RoundedCornerShape(24.dp)
                    )
                }

                MainScreen()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Update the reactive state when a new intent is received (e.g., from notification)
        activityIntent = intent
    }
}
