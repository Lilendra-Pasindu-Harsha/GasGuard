package com.example.gasml.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.gasml.ui.theme.*
import com.example.gasml.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(navController: NavController, viewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var unitCode by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Customer") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF004D40), Color(0xFF0D47A1), DarkBackground),
                    startY = 0f,
                    endY = 1500f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "Create Account",
                style = Typography.headlineMedium,
                fontSize = 28.sp,
                color = Color.White
            )
            Text(
                text = "Join Gas Guard today",
                style = Typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoleSelectionCard(
                    title = "Customer",
                    isSelected = selectedRole == "Customer",
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        selectedRole = "Customer"
                        viewModel.clearError()
                    }
                )
                RoleSelectionCard(
                    title = "Dealer",
                    isSelected = selectedRole == "Dealer",
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        selectedRole = "Dealer"
                        viewModel.clearError()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AuthTextField(
                value = name, 
                onValueChange = { 
                    name = it
                    viewModel.clearError() 
                }, 
                placeholder = "Full Name", 
                icon = Icons.Default.Person
            )
            Spacer(modifier = Modifier.height(12.dp))
            AuthTextField(
                value = email, 
                onValueChange = { 
                    email = it
                    viewModel.clearError() 
                }, 
                placeholder = "Email Address", 
                icon = Icons.Default.Email
            )
            Spacer(modifier = Modifier.height(12.dp))
            AuthTextField(
                value = password, 
                onValueChange = { 
                    password = it
                    viewModel.clearError() 
                }, 
                placeholder = "Password (min 6 characters)", 
                icon = Icons.Default.Lock, 
                isPassword = true
            )
            
            if (selectedRole == "Customer") {
                Spacer(modifier = Modifier.height(12.dp))
                AuthTextField(
                    value = unitCode,
                    onValueChange = { 
                        if (it.length <= 3 && it.all { char -> char.isDigit() }) {
                            unitCode = it
                            viewModel.clearError()
                        }
                    },
                    placeholder = "3-Digit Unit Code (e.g. 001)",
                    icon = Icons.Default.SettingsRemote
                )
            }

            viewModel.errorMessage?.let {
                Text(
                    text = it,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { 
                    val finalUnitId = if (selectedRole == "Customer") {
                        "UNIT_${unitCode.padStart(3, '0')}"
                    } else null

                    viewModel.register(name, email, selectedRole, password, finalUnitId) {
                        // On success, navigate to dashboard based on role
                        val route = if (selectedRole == "Dealer") Screen.DealerHome.route else Screen.Home.route
                        navController.navigate(route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                enabled = !viewModel.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(32.dp)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign up", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Already have an account? ", color = Color.White.copy(alpha = 0.7f))
                Text(
                    text = "Sign In",
                    color = PrimaryGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { 
                        viewModel.clearError()
                        navController.popBackStack() 
                    }
                )
            }
        }
    }
}
