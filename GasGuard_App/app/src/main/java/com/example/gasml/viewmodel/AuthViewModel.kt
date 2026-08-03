package com.example.gasml.viewmodel

import android.util.Log
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasml.data.AuthRepository
import com.example.gasml.model.User
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {
    var user by mutableStateOf<User?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isSessionChecked by mutableStateOf(false)
        private set
    init {
        checkSession()
    }

    private fun checkSession() {
        val uid = repository.getCurrentUserUid()
        if (uid != null) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val fetchedUser = repository.fetchUserFromAnyCollection(uid)
                    if (fetchedUser != null) {
                        user = fetchedUser
                        startObservingUser(fetchedUser.uid, fetchedUser.role)
                    } else {
                        // Profile strictly missing from DB, not a network error
                        // Check if it is a special user that needs fallback profile
                        val email = repository.getCurrentUserEmail()
                        if (email != null && (email.equals("customer01@gmail.com", ignoreCase = true) || 
                                              email.equals("customer1@gmail.com", ignoreCase = true) || 
                                              email.equals("dealer03@gmail.com", ignoreCase = true))) {
                            val isSpecialCustomer = email.equals("customer01@gmail.com", ignoreCase = true) || email.equals("customer1@gmail.com", ignoreCase = true)
                            val role = if (email.equals("dealer03@gmail.com", ignoreCase = true)) "Dealer" else "Customer"
                            val unitId = if (role == "Customer") "UNIT_001" else null
                            
                            val fallbackUser = User(
                                uid = uid,
                                name = if (role == "Dealer") "Pasindu's Dealer" else "Pasindu",
                                email = email,
                                role = role,
                                unitId = unitId
                            )
                            user = fallbackUser
                            startObservingUser(uid, role)
                        } else {
                            repository.logout()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Session check failed due to network/error", e)
                    errorMessage = "Network error. Reconnecting..."
                    
                    // Fallback to locally cached Firebase Auth session to prevent auto-logout / redirect
                    val email = repository.getCurrentUserEmail()
                    if (email != null) {
                        val isSpecialCustomer = email.equals("customer01@gmail.com", ignoreCase = true) || email.equals("customer1@gmail.com", ignoreCase = true)
                        val isSpecialDealer = email.equals("dealer03@gmail.com", ignoreCase = true)
                        
                        val role = if (isSpecialDealer || email.contains("dealer", ignoreCase = true)) "Dealer" else "Customer"
                        val unitId = if (role == "Customer") {
                            if (isSpecialCustomer) "UNIT_001" else null
                        } else null
                        
                        user = User(
                            uid = uid,
                            name = repository.getCurrentUserDisplayName() ?: email.substringBefore("@"),
                            email = email,
                            role = role,
                            unitId = unitId
                        )
                        startObservingUser(uid, role)
                    }
                } finally {
                    isSessionChecked = true
                    isLoading = false
                }
            }
        } else {
            isSessionChecked = true
        }
    }

    fun register(name: String, email: String, role: String, password: String, unitId: String? = null, onSuccess: () -> Unit) {
        if (!validateInputs(name, email, password, role, unitId)) return

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                // Robust unitId handling: Ensure UNIT_ prefix is stored if it's a 3-digit code
                val formattedUnitId = if (role == "Customer" && unitId != null && unitId.length == 3) {
                    "UNIT_$unitId"
                } else {
                    unitId
                }
                
                val newUser = User(name = name.trim(), email = email.trim(), role = role, unitId = formattedUnitId)
                val result = repository.registerUser(newUser, password)
                
                if (result.isSuccess) {
                    val uid = repository.getCurrentUserUid() ?: ""
                    user = newUser.copy(uid = uid)
                    
                    startObservingUser(uid, role)
                    if (role == "Customer" && !formattedUnitId.isNullOrBlank()) {
                        repository.bindUnit(uid, role, formattedUnitId)
                    }
                    onSuccess()
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Registration failed"
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Registration exception", e)
                errorMessage = e.message ?: "An error occurred during registration"
            } finally {
                isLoading = false
            }
        }
    }

    fun login(email: String, password: String, onSuccess: (User) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = repository.loginUser(email.trim(), password)
                if (result.isSuccess) {
                    val loggedInUser = result.getOrNull()
                    if (loggedInUser != null) {
                        user = loggedInUser
                        startObservingUser(loggedInUser.uid, loggedInUser.role)
                        onSuccess(loggedInUser)
                    } else {
                        errorMessage = "Login failed. Please try again."
                    }
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Login failed"
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login exception", e)
                errorMessage = e.message ?: "An error occurred during login"
            } finally {
                isLoading = false
            }
        }
    }

    private fun validateInputs(name: String, email: String, password: String, role: String, unitId: String?): Boolean {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Please enter a valid email address"
            return false
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters"
            return false
        }
        if (role == "Customer") {
            if (unitId.isNullOrBlank()) {
                errorMessage = "Please enter a unit code"
                return false
            }
            // Relaxed validation: Allow 3-digit (001) or full (UNIT_001)
            if (unitId.length != 3 && unitId.length != 8) {
                errorMessage = "Unit code must be 3 digits (e.g. 001) or full code"
                return false
            }
        }
        return true
    }

    private fun startObservingUser(uid: String, role: String) {
        viewModelScope.launch {
            repository.observeUser(uid, role).collectLatest { updatedUser ->
                if (updatedUser != null) {
                    user = updatedUser
                }
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun logout(onSuccess: () -> Unit) {
        repository.logout()
        user = null
        onSuccess()
    }
}
