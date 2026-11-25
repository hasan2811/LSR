package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val company: String = "HSSE Tech Operations",
    val role: String = "Lead HSE Inspector",
    val certification: String = "NEBOSH / AK3 Umum",
    val isEmailVerified: Boolean = false,
    val creationTime: Long = 0L
)

class FirebaseAuthManager(private val context: Context) {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val prefs: SharedPreferences = context.getSharedPreferences("lsr_auth_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            _currentUser.value = buildUserProfile(user)
        } else {
            _currentUser.value = null
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        // Initial check
        auth.currentUser?.let {
            _currentUser.value = buildUserProfile(it)
        }
    }

    private fun buildUserProfile(user: FirebaseUser): UserProfile {
        val savedCompany = prefs.getString("company_${user.uid}", "") ?: ""
        val savedRole = prefs.getString("role_${user.uid}", "") ?: ""
        val savedCert = prefs.getString("cert_${user.uid}", "") ?: ""

        val mainPrefs = context.getSharedPreferences("lsr_prefs", Context.MODE_PRIVATE)
        val defaultCompany = if (savedCompany.isNotBlank()) savedCompany else mainPrefs.getString("company_name", "HSSE Tech Operations") ?: "HSSE Tech Operations"
        val defaultRole = if (savedRole.isNotBlank()) savedRole else mainPrefs.getString("inspector_role", "Lead HSE Inspector") ?: "Lead HSE Inspector"
        val defaultCert = if (savedCert.isNotBlank()) savedCert else mainPrefs.getString("inspector_cert", "NEBOSH / AK3 Umum") ?: "NEBOSH / AK3 Umum"

        return UserProfile(
            uid = user.uid,
            email = user.email ?: "",
            displayName = user.displayName ?: (user.email?.substringBefore("@") ?: "HSE Inspector"),
            company = defaultCompany,
            role = defaultRole,
            certification = defaultCert,
            isEmailVerified = user.isEmailVerified,
            creationTime = user.metadata?.creationTimestamp ?: System.currentTimeMillis()
        )
    }

    suspend fun login(email: String, pass: String, language: AppLanguage = AppLanguage.ID): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val trimmedEmail = email.trim()
                if (trimmedEmail.isBlank() || pass.isBlank()) {
                    return@withContext Result.failure(
                        Exception(if (language == AppLanguage.ID) "Email dan kata sandi wajib diisi." else "Email and password are required.")
                    )
                }

                val authResult = auth.signInWithEmailAndPassword(trimmedEmail, pass).await()
                val user = authResult.user ?: throw Exception("User not found after login.")
                val profile = buildUserProfile(user)
                _currentUser.value = profile

                // Sync with main app preferences
                saveProfileToMainPrefs(profile)

                Result.success(profile)
            } catch (e: Exception) {
                Result.failure(mapFirebaseException(e, language))
            }
        }
    }

    suspend fun register(
        email: String,
        pass: String,
        displayName: String,
        company: String,
        role: String,
        certification: String,
        language: AppLanguage = AppLanguage.ID
    ): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val trimmedEmail = email.trim()
                val trimmedName = displayName.trim()

                if (trimmedEmail.isBlank() || pass.isBlank()) {
                    return@withContext Result.failure(
                        Exception(if (language == AppLanguage.ID) "Email dan kata sandi wajib diisi." else "Email and password are required.")
                    )
                }
                if (pass.length < 6) {
                    return@withContext Result.failure(
                        Exception(if (language == AppLanguage.ID) "Kata sandi minimal 6 karakter." else "Password must be at least 6 characters.")
                    )
                }

                val authResult = auth.createUserWithEmailAndPassword(trimmedEmail, pass).await()
                val user = authResult.user ?: throw Exception("User creation failed.")

                // Set Display Name in Firebase Auth Profile
                val nameToUse = if (trimmedName.isNotBlank()) trimmedName else trimmedEmail.substringBefore("@")
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(nameToUse)
                    .build()
                user.updateProfile(profileUpdates).await()

                // Save extended profile data in Auth prefs
                prefs.edit().apply {
                    putString("company_${user.uid}", company.ifBlank { "HSSE Tech Operations" })
                    putString("role_${user.uid}", role.ifBlank { "Lead HSE Inspector" })
                    putString("cert_${user.uid}", certification.ifBlank { "NEBOSH / AK3 Umum" })
                    apply()
                }

                val profile = UserProfile(
                    uid = user.uid,
                    email = user.email ?: trimmedEmail,
                    displayName = nameToUse,
                    company = company.ifBlank { "HSSE Tech Operations" },
                    role = role.ifBlank { "Lead HSE Inspector" },
                    certification = certification.ifBlank { "NEBOSH / AK3 Umum" },
                    isEmailVerified = user.isEmailVerified,
                    creationTime = System.currentTimeMillis()
                )
                _currentUser.value = profile

                saveProfileToMainPrefs(profile)

                Result.success(profile)
            } catch (e: Exception) {
                Result.failure(mapFirebaseException(e, language))
            }
        }
    }

    suspend fun sendPasswordReset(email: String, language: AppLanguage = AppLanguage.ID): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val trimmedEmail = email.trim()
                if (trimmedEmail.isBlank()) {
                    return@withContext Result.failure(
                        Exception(if (language == AppLanguage.ID) "Harap masukkan alamat email." else "Please enter your email address.")
                    )
                }
                auth.sendPasswordResetEmail(trimmedEmail).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(mapFirebaseException(e, language))
            }
        }
    }

    fun updateProfileLocal(company: String, role: String, cert: String) {
        val current = _currentUser.value ?: return
        val updated = current.copy(
            company = company,
            role = role,
            certification = cert
        )
        prefs.edit().apply {
            putString("company_${current.uid}", company)
            putString("role_${current.uid}", role)
            putString("cert_${current.uid}", cert)
            apply()
        }
        _currentUser.value = updated
        saveProfileToMainPrefs(updated)
    }

    fun loginDirectly(profile: UserProfile) {
        _currentUser.value = profile
        prefs.edit().apply {
            putString("company_${profile.uid}", profile.company)
            putString("role_${profile.uid}", profile.role)
            putString("cert_${profile.uid}", profile.certification)
            apply()
        }
        saveProfileToMainPrefs(profile)
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (_: Exception) {}
        _currentUser.value = null
        val mainPrefs = context.getSharedPreferences("lsr_prefs", Context.MODE_PRIVATE)
        mainPrefs.edit().apply {
            remove("inspector_uid")
            apply()
        }
    }

    private fun saveProfileToMainPrefs(profile: UserProfile) {
        val mainPrefs = context.getSharedPreferences("lsr_prefs", Context.MODE_PRIVATE)
        mainPrefs.edit().apply {
            putString("inspector_name", profile.displayName)
            putString("company_name", profile.company)
            putString("inspector_role", profile.role)
            putString("inspector_cert", profile.certification)
            putString("inspector_email", profile.email)
            putString("inspector_uid", profile.uid)
            apply()
        }
    }

    private fun mapFirebaseException(e: Exception, language: AppLanguage): Exception {
        val rawMsg = e.message ?: ""
        val isId = language == AppLanguage.ID

        return when {
            rawMsg.contains("The email address is already in use", ignoreCase = true) ||
            rawMsg.contains("EMAIL_EXISTS", ignoreCase = true) -> {
                Exception(if (isId) "Email ini sudah terdaftar di Firebase. Silakan masuk atau gunakan email lain." else "This email address is already registered. Please sign in or use another email.")
            }
            rawMsg.contains("There is no user record", ignoreCase = true) ||
            rawMsg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
            rawMsg.contains("WRONG_PASSWORD", ignoreCase = true) ||
            rawMsg.contains("USER_NOT_FOUND", ignoreCase = true) ||
            rawMsg.contains("supplied auth credential is incorrect", ignoreCase = true) ||
            rawMsg.contains("malformed or has expired", ignoreCase = true) -> {
                Exception(if (isId) "Email atau kata sandi salah, atau akun belum terdaftar." else "Invalid email or password, or account not registered.")
            }
            rawMsg.contains("The email address is badly formatted", ignoreCase = true) ||
            rawMsg.contains("INVALID_EMAIL", ignoreCase = true) -> {
                Exception(if (isId) "Format alamat email tidak valid." else "Invalid email address format.")
            }
            rawMsg.contains("Password should be at least 6 characters", ignoreCase = true) ||
            rawMsg.contains("WEAK_PASSWORD", ignoreCase = true) -> {
                Exception(if (isId) "Kata sandi terlalu lemah. Minimal 6 karakter." else "Password is too weak. Minimum 6 characters.")
            }
            rawMsg.contains("network error", ignoreCase = true) ||
            rawMsg.contains("A network error", ignoreCase = true) -> {
                Exception(if (isId) "Gangguan koneksi internet ke Firebase. Periksa jaringan Anda." else "Network error communicating with Firebase. Please check your connection.")
            }
            rawMsg.contains("blocked all requests from this device", ignoreCase = true) ||
            rawMsg.contains("TOO_MANY_ATTEMPTS_TRY_LATER", ignoreCase = true) -> {
                Exception(if (isId) "Terlalu banyak percobaan gagal. Silakan coba lagi beberapa saat lagi." else "Too many failed attempts. Please try again later.")
            }
            else -> {
                Exception(rawMsg.ifBlank { if (isId) "Terjadi kesalahan pada Firebase Auth." else "Firebase Auth error occurred." })
            }
        }
    }
}
