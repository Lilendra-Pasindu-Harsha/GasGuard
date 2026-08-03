package com.example.gasml.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gasml.model.ChatMessage
import com.example.gasml.model.Conversation
import com.example.gasml.model.User
import com.example.gasml.ui.theme.*
import com.example.gasml.viewmodel.AuthViewModel
import com.example.gasml.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel
) {
    val conversations by chatViewModel.conversations.collectAsState()
    val user = authViewModel.user

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            chatViewModel.loadConversations(uid)
        }
    }

    // UI FIXED: Removed bottomBar from internal Scaffold to prevent "Dual Nav Bar"
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Messages", style = Typography.titleLarge) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBackground),
                actions = {
                    if (user?.role == "Customer") {
                        IconButton(onClick = { navController.navigate("dealer_selection") }) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat", tint = PrimaryGreen)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(64.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No conversations yet", color = TextSecondary)
                        if (user?.role == "Customer") {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    navController.navigate("dealer_selection")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                            ) {
                                Text("Select a Dealer to Chat", color = DarkBackground)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(conversations) { conversation ->
                        val otherUserId = conversation.participantIds.find { it != user?.uid } ?: ""
                        val otherUserName = conversation.participantNames[otherUserId] ?: "User"
                        
                        ConversationItem(conversation, otherUserName) {
                            navController.navigate("chat_detail/$otherUserId/$otherUserName")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealerSelectionScreen(
    navController: NavController,
    chatViewModel: ChatViewModel
) {
    val dealers by chatViewModel.dealers.collectAsState()

    LaunchedEffect(Unit) {
        chatViewModel.loadDealers()
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Select Dealer", style = Typography.titleLarge, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        if (dealers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No dealers available", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(dealers) { dealer ->
                    DealerItem(dealer) {
                        navController.navigate("chat_detail/${dealer.uid}/${dealer.name}") {
                            popUpTo("dealer_selection") { inclusive = true }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DealerItem(dealer: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(PrimaryGreen.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    dealer.name.take(1).uppercase(),
                    color = PrimaryGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(dealer.name, style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(dealer.address, style = Typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun ConversationItem(conversation: Conversation, otherUserName: String, onClick: () -> Unit) {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeString = sdf.format(Date(conversation.lastTimestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(PrimaryGreen.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    otherUserName.take(1).uppercase(),
                    color = PrimaryGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(otherUserName, style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(timeString, style = Typography.labelSmall, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    conversation.lastMessage,
                    style = Typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    otherUserId: String,
    otherUserName: String
) {
    var messageText by remember { mutableStateOf("") }
    val messages by chatViewModel.messages.collectAsState()
    val user = authViewModel.user

    LaunchedEffect(user?.uid, otherUserId) {
        user?.uid?.let { uid ->
            chatViewModel.loadMessages(uid, otherUserId)
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(otherUserName, style = Typography.titleMedium, color = TextPrimary)
                        Text("Online", style = Typography.labelSmall, color = PrimaryGreen)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages.asReversed()) { message ->
                    ChatBubble(message, message.senderId == user?.uid)
                }
            }

            Surface(
                color = CardBackground,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message...", color = TextSecondary) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank() && user != null) {
                                chatViewModel.sendMessage(
                                    senderId = user.uid,
                                    senderName = user.name,
                                    receiverId = otherUserId,
                                    receiverName = otherUserName,
                                    text = messageText
                                )
                                messageText = ""
                            }
                        },
                        containerColor = PrimaryGreen,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Send, null, tint = DarkBackground, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isMe: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMe) PrimaryGreen else SurfaceColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 2.dp,
                bottomEnd = if (isMe) 2.dp else 16.dp
            )
        ) {
            Text(
                text = message.text,
                color = if (isMe) DarkBackground else TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}
