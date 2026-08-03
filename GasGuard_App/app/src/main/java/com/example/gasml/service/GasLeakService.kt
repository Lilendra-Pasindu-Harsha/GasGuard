package com.example.gasml.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.gasml.data.ChatRepository
import com.example.gasml.data.GasStatsRepository
import com.example.gasml.data.OrderRepository
import com.example.gasml.model.Order
import com.example.gasml.util.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class GasLeakService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationHelper: NotificationHelper
    private val repository = GasStatsRepository()
    private val orderRepository = OrderRepository()
    private val chatRepository = ChatRepository()
    private var statsJob: Job? = null
    private var deviceStatusJob: Job? = null
    private var orderJob: Job? = null
    private var chatJob: Job? = null
    
    private val lastOrderStatuses = mutableMapOf<String, String>()
    private val lastChatTimestamps = mutableMapOf<String, Long>()
    private var lastLeakStatus = false // Track last leak state to prevent spam

    private var activeUnitId: String? = null
    private var activeUserId: String? = null
    private var activeUserRole: String? = null
    private var activeDeviceId: String? = null
    
    private var isSimulationActive = false
    private var isDeviceLeakActive = false

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "gas_leak_service_channel_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Gas Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Using standard public icon to avoid crash (Resources$NotFoundException)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GasGuard is active")
            .setContentText("Monitoring for safety and updates...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(999, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(999, notification)
            }
        } catch (e: Exception) {
            Log.e("GasLeakService", "Failed to start foreground service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val unitId = intent?.getStringExtra("UNIT_ID")
        val userId = intent?.getStringExtra("USER_ID")
        val role = intent?.getStringExtra("USER_ROLE")

        if (!unitId.isNullOrBlank() && unitId != activeUnitId) {
            startObservingStats(unitId)
        }
        
        if (!userId.isNullOrBlank() && !role.isNullOrBlank()) {
            if (userId != activeUserId || role != activeUserRole) {
                activeUserId = userId
                activeUserRole = role
                startObservingOrders(userId, role)
                startObservingChats(userId)
            }
        }
        
        return START_STICKY
    }

    private fun startObservingStats(unitId: String) {
        val formattedId = if (unitId.startsWith("UNIT_")) unitId else "UNIT_$unitId"
        if (activeUnitId == formattedId && statsJob?.isActive == true) return
        activeUnitId = formattedId

        statsJob?.cancel()
        statsJob = serviceScope.launch {
            try {
                repository.getUnitStats(formattedId).collectLatest { stats ->
                    if (stats != null) {
                        isSimulationActive = stats.leakDetected
                        updateLeakAlertState()
                        
                        val devId = stats.deviceId.ifBlank { "gasguard-esp32-01" }
                        startObservingDeviceStatus(devId)
                    }
                }
            } catch (e: Exception) {
                Log.e("GasLeakService", "Error in stats observer", e)
            }
        }
    }

    private fun startObservingDeviceStatus(deviceId: String) {
        if (activeDeviceId == deviceId && deviceStatusJob?.isActive == true) return
        activeDeviceId = deviceId

        deviceStatusJob?.cancel()
        deviceStatusJob = serviceScope.launch {
            try {
                repository.getDeviceStatus(deviceId).collectLatest { status ->
                    isDeviceLeakActive = status?.alarmActive ?: status?.gasDetected ?: false
                    updateLeakAlertState()
                }
            } catch (e: Exception) {
                Log.e("GasLeakService", "Error in device status leak observer", e)
            }
        }
    }

    private fun updateLeakAlertState() {
        val currentLeak = isSimulationActive || isDeviceLeakActive

        // Only trigger notification if state CHANGES to true
        if (currentLeak && !lastLeakStatus) {
            notificationHelper.showLeakAlert()
        } else if (!currentLeak && lastLeakStatus) {
            notificationHelper.clearAlert()
        }

        lastLeakStatus = currentLeak
    }

    private fun startObservingOrders(userId: String, role: String) {
        orderJob?.cancel()
        var isFirstEmission = true
        orderJob = serviceScope.launch {
            try {
                val orderFlow = if (role == "Dealer") {
                    orderRepository.getAllOrdersForDealer()
                } else {
                    orderRepository.getCustomerOrders(userId)
                }

                orderFlow.collectLatest { orders ->
                    if (isFirstEmission) {
                        orders.forEach { lastOrderStatuses[it.id] = it.status }
                        isFirstEmission = false
                        return@collectLatest
                    }

                    orders.forEach { order ->
                        val lastStatus = lastOrderStatuses[order.id]

                        if (role == "Dealer") {
                            if (lastStatus == null && order.status == "Pending") {
                                notificationHelper.showOrderNotification(
                                    "New Order Received",
                                    "Order from ${order.userName} for ${order.cylinderType}"
                                )
                            }
                        } else {
                            if (lastStatus != null && lastStatus != order.status) {
                                notificationHelper.showOrderNotification(
                                    "Order Update",
                                    "Order ${order.cylinderType} is now: ${order.status}"
                                )
                            }
                        }
                        lastOrderStatuses[order.id] = order.status
                    }
                }
            } catch (e: Exception) {
                Log.e("GasLeakService", "Error in order observer", e)
            }
        }
    }

    private fun startObservingChats(userId: String) {
        chatJob?.cancel()
        var isFirstEmission = true
        chatJob = serviceScope.launch {
            try {
                chatRepository.getConversations(userId).collectLatest { conversations ->
                    if (isFirstEmission) {
                        conversations.forEach { lastChatTimestamps[it.chatId] = it.lastTimestamp }
                        isFirstEmission = false
                        return@collectLatest
                    }

                    conversations.forEach { convo ->
                        val lastTs = lastChatTimestamps[convo.chatId] ?: 0L
                        val currentTs = convo.lastTimestamp

                        if (currentTs > lastTs && convo.lastSenderId != userId) {
                            val senderName = convo.participantNames.entries.find { it.key != userId }?.value ?: "User"
                            notificationHelper.showChatNotification(senderName, convo.lastMessage)
                        }
                        lastChatTimestamps[convo.chatId] = currentTs
                    }
                }
            } catch (e: Exception) {
                Log.e("GasLeakService", "Error in chat observer", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
