package com.example.gasml.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.gasml.util.Constants
import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val databaseRef = FirebaseDatabase.getInstance(Constants.DATABASE_URL).getReference("inventory")

    private val _inventory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val inventory: StateFlow<Map<String, Int>> = _inventory.asStateFlow()

    private val defaultStock = mapOf(
        "standard_5kg" to 142,
        "small_2_5kg" to 45,
        "large_12_5kg" to 12
    )

    init {
        observeInventory()
    }

    private fun observeInventory() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val map = mutableMapOf<String, Int>()
                    for (child in snapshot.children) {
                        val key = child.key ?: continue
                        val value = child.getValue(Int::class.java) ?: 0
                        map[key] = value
                    }
                    _inventory.value = map
                } else {
                    initializeDefaultInventory()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("InventoryViewModel", "Failed to read inventory", error.toException())
            }
        })
    }

    private fun initializeDefaultInventory() {
        databaseRef.setValue(defaultStock)
        _inventory.value = defaultStock
    }

    fun updateStock(itemKey: String, count: Int) {
        val updatedCount = count.coerceAtLeast(0)
        databaseRef.child(itemKey).setValue(updatedCount)
    }
}
