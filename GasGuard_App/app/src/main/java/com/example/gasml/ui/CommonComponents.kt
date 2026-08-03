package com.example.gasml.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gasml.ui.theme.*

// --- Modifier Extensions ---

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

// --- Auth Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.5f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = PrimaryGreen) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CardBackground.copy(alpha = 0.5f),
            unfocusedContainerColor = CardBackground.copy(alpha = 0.5f),
            focusedIndicatorColor = PrimaryGreen,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun FeatureChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectionCard(title: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryGreen else CardBackground
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                color = if (isSelected) DarkBackground else TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// --- Stats Components ---

@Composable
fun RefillPredictionCard(daysRemaining: Int, refillDate: String, predictedUsage: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Next Refill", color = TextSecondary, style = Typography.labelSmall)
                Text(refillDate, color = PrimaryGreen, style = Typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Estimated Date", color = TextSecondary.copy(0.6f), fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(TextSecondary.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$daysRemaining Days", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(predictedUsage, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(color.copy(0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(label, color = TextSecondary, style = Typography.labelSmall)
                Text(value, color = TextPrimary, style = Typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
