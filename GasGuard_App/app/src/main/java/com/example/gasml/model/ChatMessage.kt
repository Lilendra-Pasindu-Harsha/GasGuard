package com.example.gasml.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromUser: Boolean = true
)
