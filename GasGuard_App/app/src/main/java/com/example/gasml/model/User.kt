package com.example.gasml.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "Customer",
    val phoneNumber: String = "",
    val address: String = "",
    val unitId: String? = null
)
