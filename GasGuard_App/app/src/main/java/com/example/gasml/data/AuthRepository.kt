package com.example.gasml.data

import android.util.Log
import com.example.gasml.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.gasml.util.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance(Constants.DATABASE_URL)

    private val SPECIAL_CUSTOMER_UID = "JRE6z2ktx1UeCFAlYrYtKLOGLue2"
    private val SPECIAL_CUSTOMER_UNIT_ID = "UNIT_001"
    private val SPECIAL_DEALER_UID = "Cmje4qn6KNV0TGL2us9mm7hsKPD3"

    private fun getCollection(role: String) = if (role.equals("Dealer", ignoreCase = true)) "dealers" else "customers"

    private fun isSpecialCustomer(email: String?): Boolean {
        if (email == null) return false
        return email.equals("customer01@gmail.com", ignoreCase = true) || 
               email.equals("customer1@gmail.com", ignoreCase = true)
    }
    
    private fun isSpecialDealer(email: String?): Boolean {
        if (email == null) return false
        return email.equals("dealer03@gmail.com", ignoreCase = true)
    }

    suspend fun registerUser(user: User, password: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(user.email, password).await()
            val uid = result.user?.uid ?: throw Exception("User creation failed")
            val newUser = user.copy(uid = uid)

            try {
                database.getReference(getCollection(user.role)).child(uid).setValue(newUser).await()
                Result.success(Unit)
            } catch (e: Exception) {
                auth.currentUser?.delete()?.await()
                throw Exception("Failed to create user profile.")
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(Exception("An account already exists with this email address."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Registration failed."))
        }
    }

    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val realUid = authResult.user?.uid ?: throw Exception("Authentication failed")
            
            var user: User? = null
            var dbError: Throwable? = null
            try {
                user = fetchUserFromAnyCollection(realUid)
            } catch (e: Exception) {
                dbError = e
            }
            
            if (user == null) {
                if (isSpecialCustomer(email)) {
                    user = User(uid = SPECIAL_CUSTOMER_UID, name = "Pasindu", email = email, role = "Customer", unitId = SPECIAL_CUSTOMER_UNIT_ID)
                } else if (isSpecialDealer(email)) {
                    user = User(uid = SPECIAL_DEALER_UID, name = "Pasindu's Dealer", email = email, role = "Dealer", unitId = null)
                } else if (dbError != null) {
                    throw dbError
                }
            }
            
            if (user == null) throw Exception("Profile not found. Please register again.")
            Result.success(user)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(Exception("No account found with this email address."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Incorrect password."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Login failed. Check connection."))
        }
    }

    suspend fun fetchUserFromAnyCollection(uid: String): User? = coroutineScope {
        try {
            withTimeout(5000) {
                // Run checks in parallel to save time
                val customerDeferred = async { 
                    database.getReference("customers").child(uid).get().await() 
                }
                val dealerDeferred = async { 
                    database.getReference("dealers").child(uid).get().await() 
                }
        
                val custSnapshot = customerDeferred.await()
                if (custSnapshot.exists()) {
                    return@withTimeout custSnapshot.getValue(User::class.java)?.copy(uid = uid)
                }
        
                val dealerSnapshot = dealerDeferred.await()
                if (dealerSnapshot.exists()) {
                    return@withTimeout dealerSnapshot.getValue(User::class.java)?.copy(uid = uid)
                }
        
                null
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e("AuthRepository", "Database query timed out for uid: $uid", e)
            throw Exception("Database connection timed out. Please check your internet connection.")
        }
    }

    fun observeUser(uid: String, role: String): Flow<User?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        
        val currentEmail = auth.currentUser?.email
        val targetUid = when {
            isSpecialCustomer(currentEmail) -> SPECIAL_CUSTOMER_UID
            isSpecialDealer(currentEmail) -> SPECIAL_DEALER_UID
            else -> uid
        }
        val targetRole = when {
            isSpecialCustomer(currentEmail) -> "Customer"
            isSpecialDealer(currentEmail) -> "Dealer"
            else -> role
        }
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val u = try { snapshot.getValue(User::class.java)?.copy(uid = targetUid) } catch (e: Exception) { null }
                trySend(u)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }

        val ref = database.getReference(getCollection(targetRole)).child(targetUid)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun bindUnit(userId: String, role: String, unitId: String): Result<Unit> {
        return try {
            database.getReference(getCollection(role)).child(userId).child("unitId").setValue(unitId).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUserUid(): String? {
        val email = auth.currentUser?.email
        return when {
            isSpecialCustomer(email) -> SPECIAL_CUSTOMER_UID
            isSpecialDealer(email) -> SPECIAL_DEALER_UID
            else -> auth.currentUser?.uid
        }
    }

    fun getCurrentUserEmail(): String? = auth.currentUser?.email
    fun getCurrentUserDisplayName(): String? = auth.currentUser?.displayName

    fun logout() = auth.signOut()
}
