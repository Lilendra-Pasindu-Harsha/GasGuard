package com.example.gasml.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasml.data.OrderRepository
import com.example.gasml.model.Order
import com.example.gasml.util.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OrderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OrderRepository()
    private val notificationHelper = NotificationHelper(application)
    
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders

    val activeOrdersCount = _orders.map { list ->
        list.count { it.status != "Delivered" && it.status != "Cancelled" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalRevenue: StateFlow<Double> = _orders.map { list ->
        list.filter { it.status == "Delivered" }
            .fold(0.0) { acc, order -> acc + order.getEffectiveTotalPrice() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private var ordersJob: Job? = null

    var isPlacingOrder by mutableStateOf(false)
        private set

    fun placeOrder(order: Order, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (isPlacingOrder) return
        viewModelScope.launch {
            isPlacingOrder = true
            try {
                repository.placeOrder(order).getOrThrow()
                
                notificationHelper.showOrderNotification(
                    "Order Placed Successfully",
                    "Your request for ${order.cylinderType} refill has been received."
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("OrderViewModel", "Place order failed", e)
                onError(e.message ?: "Failed to place order")
            } finally {
                isPlacingOrder = false
            }
        }
    }

    fun loadCustomerOrders(userId: String) {
        if (userId.isBlank()) return
        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            repository.getCustomerOrders(userId).collect { orderList ->
                _orders.value = orderList
            }
        }
    }

    fun loadDealerOrders() {
        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            repository.getAllOrdersForDealer().collect { orderList ->
                _orders.value = orderList
            }
        }
    }

    fun updateStatus(orderId: String, newStatus: String) {
        if (orderId.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.updateOrderStatus(orderId, newStatus).getOrThrow()
            } catch (e: Exception) {
                Log.e("OrderViewModel", "Status update failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ordersJob?.cancel()
    }
}
