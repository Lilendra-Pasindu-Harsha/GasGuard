package com.example.gasml.data

import android.util.Log
import com.example.gasml.model.Order
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.gasml.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class OrderRepository {
    private val ordersRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).reference.child("orders")

    suspend fun placeOrder(order: Order): Result<Unit> {
        return try {
            val orderRef = ordersRef.push()
            val orderId = orderRef.key ?: throw IllegalStateException("Could not create order id")
            val now = System.currentTimeMillis()
            
            val finalOrder = order.copy(
                id = orderId,
                timestamp = now,
                safeTimestamp = now,
                safeQuantity = order.quantity,
                safeTotalPrice = order.totalPrice
            )

            orderRef.setValue(finalOrder).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Place order failed", e)
            Result.failure(e)
        }
    }

    fun getCustomerOrders(userId: String): Flow<List<Order>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query = ordersRef.orderByChild("userId").equalTo(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = snapshot.children.mapNotNull { child ->
                    child.toOrder()
                }.sortedByDescending { it.getEffectiveTimestampMillis() }
                trySend(orders)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("OrderRepository", "Orders listener failed", error.toException())
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    fun getAllOrdersForDealer(): Flow<List<Order>> = callbackFlow {
        val query = ordersRef.orderByChild("timestamp")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = snapshot.children.mapNotNull { child ->
                    child.toOrder()
                }.sortedByDescending { it.getEffectiveTimestampMillis() }
                trySend(orders)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("OrderRepository", "Dealer listener failed", error.toException())
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit> {
        return try {
            ordersRef.child(orderId).child("status").setValue(newStatus).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Update status failed", e)
            Result.failure(e)
        }
    }

    private fun DataSnapshot.toOrder(): Order? {
        return try {
            getValue(Order::class.java)?.copy(id = key ?: "")
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to parse order $key", e)
            null
        }
    }
}
