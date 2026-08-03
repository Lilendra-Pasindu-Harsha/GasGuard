package com.example.gasml.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasml.data.ChatRepository
import com.example.gasml.model.ChatMessage
import com.example.gasml.model.Conversation
import com.example.gasml.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(private val repository: ChatRepository = ChatRepository()) : ViewModel() {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _dealers = MutableStateFlow<List<User>>(emptyList())
    val dealers: StateFlow<List<User>> = _dealers.asStateFlow()

    private var currentChatId: String? = null

    /**
     * Loads all conversations for the current user.
     */
    fun loadConversations(userId: String) {
        viewModelScope.launch {
            repository.getConversations(userId).collect {
                _conversations.value = it
            }
        }
    }

    /**
     * Loads messages between the current user and a specific target.
     */
    fun loadMessages(currentUserId: String, otherUserId: String) {
        val chatId = repository.generateChatId(currentUserId, otherUserId)
        currentChatId = chatId
        viewModelScope.launch {
            repository.getMessages(chatId).collect {
                _messages.value = it
            }
        }
    }

    /**
     * Fetches the list of all dealers for the customer to start a new chat.
     */
    fun loadDealers() {
        viewModelScope.launch {
            _dealers.value = repository.getDealers()
        }
    }

    fun sendMessage(senderId: String, senderName: String, receiverId: String, receiverName: String, text: String) {
        val chatId = repository.generateChatId(senderId, receiverId)
        val message = ChatMessage(
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            text = text,
            isFromUser = true
        )
        viewModelScope.launch {
            repository.sendMessage(message, senderName, receiverName)
        }
    }
}
