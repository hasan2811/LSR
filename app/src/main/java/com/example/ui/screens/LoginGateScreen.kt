package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.components.HseBrandLogo
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginGateScreen(
    language: AppLanguage,
    isLoading: Boolean,
    errorMessage: String?,
    firestoreUsers: List<FirestoreUser>,
    firestoreProjects: List<FirestoreProject>,
    onLogin: (email: String, pass: String) -> Unit,
    onQuickLogin: (FirestoreUser) -> Unit,
    onRegister: (email: String, pass: String, name: String, company: String, role: String, cert: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onClearError: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeGateTab by remember { mutableIntStateOf(0) } // 0: Masuk, 1: Pilih Akun Firestore, 2: Daftar Baru
    val focusManager = LocalFocusManager.current

    // Sign in form state
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Sign up form state
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regConfirmPass by remember { mutableStateOf("") }
    var regCompany by remember { mutableStateOf("") }
    var regRole by remember { mutableStateOf("") }
    var regCert by remember { mutableStateOf("") }

    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val isId = language == AppLanguage.ID

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkNavySurface,
                        Color(0xFF0F172A),
                        Color(0xFF090D16)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Language Switcher & Security Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SafetyTeal.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, SafetyTeal.copy(alpha = 0.35f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = SafetyTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "SECURE ENTERPRISE GATEWAY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = SafetyTeal,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onToggleLanguage,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isId) "ID 🇮🇩" else "EN 🇬🇧",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // App Brand Hero Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HseBrandLogo(size = 72.dp)
                Text(
                    text = if (isId) "HSSE Life-Saving Rules" else "HSSE Life-Saving Rules",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isId) "Sistem Pengawasan K3 Terpadu & Audit Lapangan" else "Integrated HSE Inspection & Field Audit System",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                // Firestore sync status indicator
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SafetyBlue.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, SafetyBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(AmberGold)
                        )
                        Text(
                            text = "🔥 Terkoneksi Firestore (Koleksi 'users' & 'projects')",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tab Selector Card
            TabRow(
                selectedTabIndex = activeGateTab,
                containerColor = Color(0xFF1E293B),
                contentColor = SafetyBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Tab(
                    selected = activeGateTab == 0,
                    onClick = {
                        activeGateTab = 0
                        validationError = null
                        onClearError()
                    },
                    text = {
                        Text(
                            text = if (isId) "Masuk" else "Sign In",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = activeGateTab == 1,
                    onClick = {
                        activeGateTab = 1
                        validationError = null
                        onClearError()
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.PeopleAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                text = if (isId) "Akun Terdaftar (${firestoreUsers.size})" else "Registered (${firestoreUsers.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                )
                Tab(
                    selected = activeGateTab == 2,
                    onClick = {
                        activeGateTab = 2
                        validationError = null
                        onClearError()
                    },
                    text = {
                        Text(
                            text = if (isId) "Daftar" else "Sign Up",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                )
            }

            // Error Banner
            val currentError = validationError ?: errorMessage
            if (!currentError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DangerRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = currentError,
                            fontSize = 11.sp,
                            color = Color.White,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                validationError = null
                                onClearError()
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // MAIN CONTENT CARD BASED ON TAB
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (activeGateTab) {
                        0 -> {
                            // SIGN IN FORM
                            Text(
                                text = if (isId) "Masuk dengan Kredensial K3" else "Sign In with HSE Credentials",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text(if (isId) "Alamat Email K3" else "Email Address") },
                                leadingIcon = {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = SafetyBlue)
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_email_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SafetyBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedLabelColor = SafetyBlue,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text(if (isId) "Kata Sandi" else "Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = SafetyBlue)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    if (emailInput.isBlank() || passwordInput.isBlank()) {
                                        validationError = if (isId) "Email dan kata sandi wajib diisi." else "Email and password are required."
                                    } else {
                                        onLogin(emailInput, passwordInput)
                                    }
                                }),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_password_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SafetyBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedLabelColor = SafetyBlue,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        forgotEmail = emailInput
                                        showForgotDialog = true
                                    }
                                ) {
                                    Text(
                                        text = if (isId) "Lupa Kata Sandi?" else "Forgot Password?",
                                        fontSize = 11.sp,
                                        color = SafetyBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (emailInput.isBlank() || passwordInput.isBlank()) {
                                        validationError = if (isId) "Email dan kata sandi wajib diisi." else "Email and password are required."
                                    } else {
                                        onLogin(emailInput, passwordInput)
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_submit_login"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(if (isId) "Memproses Verifikasi..." else "Verifying...")
                                } else {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isId) "Masuk ke Sistem K3" else "Sign In to HSE Portal",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        1 -> {
                            // SELECT REGISTERED USER FROM FIRESTORE
                            Text(
                                text = if (isId) "Daftar User Firestore (Koleksi 'users')" else "Firestore Users Directory ('users' collection)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isId)
                                    "Pilih salah satu personil di bawah untuk langsung masuk sesuai izin proyek Firestore:"
                                else
                                    "Select any registered personnel below to immediately enter their authorized projects:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )

                            if (firestoreUsers.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudQueue,
                                            contentDescription = null,
                                            tint = SafetyTeal,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = if (isId) "Terhubung ke Firebase (lowerzakum-482af)" else "Connected to Firebase (lowerzakum-482af)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = if (isId)
                                                "Belum ada akun personil di database. Silakan masuk dengan email terdaftar di tab 'Masuk', atau buat akun baru di tab 'Daftar'."
                                            else
                                                "No personnel registered in database yet. Please sign in with your email in 'Sign In' tab, or register a new account in 'Sign Up' tab.",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center,
                                            lineHeight = 16.sp
                                        )
                                        OutlinedButton(
                                            onClick = { activeGateTab = 2 },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(if (isId) "Buka Pendaftaran Akun" else "Open Registration")
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    firestoreUsers.forEach { user ->
                                        val initial = if (user.displayName.isNotBlank()) user.displayName.take(2).uppercase() else "US"
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color(0xFF0F172A),
                                            border = BorderStroke(1.dp, SafetyBlue.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onQuickLogin(user)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = SafetyBlue,
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = initial,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = user.displayName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "${user.getEffectiveRole()} • ${user.getEffectiveCompany()}",
                                                        fontSize = 10.sp,
                                                        color = SafetyTeal
                                                    )
                                                    Text(
                                                        text = user.email,
                                                        fontSize = 10.sp,
                                                        color = Color.White.copy(alpha = 0.6f)
                                                    )
                                                    if (user.projectIds.isNotEmpty()) {
                                                        Text(
                                                            text = "📁 ${user.projectIds.size} Proyek Terdaftar",
                                                            fontSize = 9.sp,
                                                            color = AmberGold,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }

                                                FilledTonalButton(
                                                    onClick = { onQuickLogin(user) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = SafetyBlue.copy(alpha = 0.25f),
                                                        contentColor = SafetyBlue
                                                    )
                                                ) {
                                                    Text("Pilih", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // SIGN UP FORM
                            Text(
                                text = if (isId) "Pendaftaran Personil K3 Baru" else "Register New HSE Personnel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            OutlinedTextField(
                                value = regName,
                                onValueChange = { regName = it },
                                label = { Text(if (isId) "Nama Lengkap" else "Full Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = SafetyBlue) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SafetyBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedLabelColor = SafetyBlue,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it },
                                label = { Text(if (isId) "Alamat Email" else "Email Address") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = SafetyBlue) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SafetyBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedLabelColor = SafetyBlue,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            OutlinedTextField(
                                value = regPass,
                                onValueChange = { regPass = it },
                                label = { Text(if (isId) "Kata Sandi (Min 6 Karakter)" else "Password (Min 6 chars)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SafetyBlue) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SafetyBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedLabelColor = SafetyBlue,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            OutlinedTextField(
                                value = regConfirmPass,
                                onValueChange = { regConfirmPass = it },
                                label = { Text(if (isId) "Konfirmasi Kata Sandi" else "Confirm Password") },
                                leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null, tint = SafetyBlue) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SafetyBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedLabelColor = SafetyBlue,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            OutlinedTextField(
                                value = regCompany,
                                onValueChange = { regCompany = it },
                                label = { Text(if (isId) "Perusahaan / Kontraktor" else "Company") },
                                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = SafetyBlue) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SafetyBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedLabelColor = SafetyBlue,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            OutlinedTextField(
                                value = regRole,
                                onValueChange = { regRole = it },
                                label = { Text(if (isId) "Jabatan / Role K3" else "Role / Position") },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = SafetyBlue) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SafetyBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedLabelColor = SafetyBlue,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            Button(
                                onClick = {
                                    if (regName.isBlank() || regEmail.isBlank() || regPass.isBlank()) {
                                        validationError = if (isId) "Semua kolom wajib diisi." else "All fields are required."
                                    } else if (regPass.length < 6) {
                                        validationError = if (isId) "Kata sandi minimal 6 karakter." else "Password must be at least 6 characters."
                                    } else if (regPass != regConfirmPass) {
                                        validationError = if (isId) "Konfirmasi kata sandi tidak cocok." else "Passwords do not match."
                                    } else {
                                        onRegister(regEmail, regPass, regName, regCompany, regRole, regCert)
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyTeal)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(if (isId) "Mendaftarkan..." else "Registering...")
                                } else {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isId) "Buat Akun & Sinkron Firestore" else "Create Account & Sync Firestore",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Security Footnote
            Text(
                text = if (isId)
                    "🔒 Sesuai Standar IOGP 459 & OSHA • Data Dienkripsi End-to-End dengan Firebase Security Rules"
                else
                    "🔒 Compliant with IOGP 459 & OSHA • End-to-End Encrypted via Firebase Security Rules",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }

    // Forgot Password Dialog
    if (showForgotDialog) {
        Dialog(onDismissRequest = { showForgotDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = SafetyBlue)
                        Text(
                            text = if (isId) "Reset Kata Sandi" else "Reset Password",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (isId)
                            "Masukkan alamat email Anda untuk menerima tautan reset kata sandi dari Firebase Authentication."
                        else
                            "Enter your email to receive a password reset link from Firebase Authentication.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showForgotDialog = false }) {
                            Text(if (isId) "Batal" else "Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (forgotEmail.isNotBlank()) {
                                    onResetPassword(forgotEmail)
                                    showForgotDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue)
                        ) {
                            Text(if (isId) "Kirim Tautan" else "Send Link")
                        }
                    }
                }
            }
        }
    }
}
