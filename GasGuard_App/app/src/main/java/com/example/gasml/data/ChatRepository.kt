package com.example.gasml.data

import android.util.Log
import com.example.gasml.model.ChatMessage
import com.example.gasml.model.Conversation
import com.example.gasml.model.User
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.gasml.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class ChatRepository {
    private val database = FirebaseDatabase.getInstance(Constants.DATABASE_URL)
    private val chatRef = database.getReference("chats")
    private val conversationRef = database.getReference("conversations")
    private val dealerRef = database.getReference("dealers")

    suspend fun sendMessage(message: ChatMessage, senderName: String, receiverName: String): Result<Unit> {
        return try {
            val newMessageRef = chatRef.push()
            val finalMessage = message.copy(id = newMessageRef.key ?: "")
            
            newMessageRef.setValue(finalMessage).await()
            
            val conversationData = mapOf(
                "chatId" to message.chatId,
                "participantIds" to listOf(message.senderId, message.receiverId),
                "participantNames" to mapOf(
                    message.senderId to senderName,
                    message.receiverId to receiverName
                ),
                "lastMessage" to message.text,
                "lastTimestamp" to message.timestamp,
                "lastSenderId" to message.senderId
            )
            
            conversationRef.child(message.chatId).updateChildren(conversationData).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to send message", e)
            Result.failure(e)
        }
    }

    fun getMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val query = chatRef.orderByChild("chatId").equalTo(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { child ->
                    try {
                        child.getValue(ChatMessage::class.java)?.copy(id = child.key ?: "")
                    } catch (e: Exception) {
                        Log.e("ChatRepository", "Failed to parse message ${child.key}", e)
                        null
                    }
                }
                trySend(messages.sortedBy { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatRepository", "Messages listener failed", error.toException())
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    fun getConversations(userId: String): Flow<List<Conversation>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val convos = snapshot.children.mapNotNull { child ->
                    try {
                        val convo = child.getValue(Conversation::class.java)
                        if (convo?.participantIds?.contains(userId) == true) convo else null
                    } catch (e: Exception) {
                        Log.e("ChatRepository", "Failed to parse conversation ${child.key}", e)
                        null
                    }
                }
                trySend(convos.sortedByDescending { it.lastTimestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatRepository", "Conversations listener failed", error.toException())
                close(error.toException())
            }
        }
        conversationRef.addValueEventListener(listener)
        awaitClose { conversationRef.removeEventListener(listener) }
    }

    suspend fun getDealers(): List<User> {
        return try {
            withTimeout(5000) {
                val snapshot = dealerRef.get().await()
                snapshot.children.mapNotNull { it.getValue(User::class.java) }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to fetch dealers", e)
            emptyList()
        }
    }

    fun generateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"
    }
}
