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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gasml.ui.theme.*
import com.example.gasml.viewmodel.AuthViewModel

@Composable
fun LoginScreen(navController: NavController, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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
            Spacer(modifier = Modifier.height(60.dp))
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = PrimaryGreen, modifier = Modifier.size(40.dp))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("Gas Guard", style = Typography.headlineMedium, fontSize = 32.sp, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "LPG intelligence, safety alerts and on-demand delivery.",
                style = Typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            AuthTextField(
                value = email, 
                onValueChange = { 
                    email = it
                    viewModel.clearError() 
                }, 
                placeholder = "Email address", 
                icon = Icons.Default.Email
            )
            Spacer(modifier = Modifier.height(16.dp))
            AuthTextField(
                value = password, 
                onValueChange = { 
                    password = it
                    viewModel.clearError() 
                }, 
                placeholder = "Password", 
                icon = Icons.Default.Lock, 
                isPassword = true
            )
            
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
                    viewModel.login(email, password) { user ->
                        val route = if (user.role == "Dealer") Screen.DealerHome.route else Screen.Home.route
                        navController.navigate(route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                enabled = !viewModel.isLoading,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(32.dp)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign in →", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(modifier = Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account? ", color = Color.White.copy(alpha = 0.7f))
                Text(
                    "Register",
                    color = PrimaryGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { 
                        viewModel.clearError()
                        navController.navigate(Screen.Register.route) 
                    }
                )
            }
        }
    }
}
