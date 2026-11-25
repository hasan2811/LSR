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
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
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
    onRegister: (email: String, pass: String, name: String, company: String, role: String, cert: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onClearError: () -> Unit,
    onToggleLanguage: () -> Unit,
    scriptUrl: String = GoogleAppsScriptSnippet.DEFAULT_WEBHOOK_URL,
    isGasEnabled: Boolean = true,
    isDatabaseAdminUnlocked: Boolean = false,
    onUnlockDatabaseAdmin: (String) -> Boolean = { false },
    onSaveAndTestDatabaseSetup: (
        adminPassword: String,
        newScriptUrl: String,
        gasEnabled: Boolean,
        publishGlobal: Boolean,
        onResult: (Boolean, String) -> Unit
    ) -> Unit = { _, _, _, _, onRes -> onRes(false, "Not configured") },
    modifier: Modifier = Modifier
) {
    var activeGateTab by remember { mutableIntStateOf(0) } // 0: Masuk, 1: Pilih Akun Firestore, 2: Daftar Baru
    val focusManager = LocalFocusManager.current

    // Sign in form state
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var selectedAccountHint by remember { mutableStateOf<String?>(null) }

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
    var showDatabaseSetupDialog by remember { mutableStateOf(false) }
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

                            if (!selectedAccountHint.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SafetyTeal.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, SafetyTeal.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = selectedAccountHint!!,
                                        fontSize = 11.sp,
                                        color = SafetyTeal,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = {
                                    emailInput = it
                                    selectedAccountHint = null
                                },
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
                            // SELECT REGISTERED USER FROM DIRECTORY (Autofill Email -> Require Password)
                            Text(
                                text = if (isId) "Direktori Personil K3 Terdaftar" else "Registered HSE Personnel Directory",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isId)
                                    "Pilih personil di bawah untuk mengisi email secara otomatis, lalu masukkan kata sandi di tab Masuk:"
                                else
                                    "Select personnel below to autofill their email, then enter your password in the Sign In tab:",
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
                                    val selectAccountAction: (FirestoreUser) -> Unit = { user ->
                                        emailInput = user.email
                                        passwordInput = ""
                                        validationError = null
                                        onClearError()
                                        selectedAccountHint = if (isId) {
                                            "Akun ${user.displayName} (${user.email}) dipilih. Masukkan kata sandi untuk melanjutkan."
                                        } else {
                                            "Selected ${user.displayName} (${user.email}). Enter your password to continue."
                                        }
                                        activeGateTab = 0
                                    }
                                    firestoreUsers.forEach { user ->
                                        val initial = if (user.displayName.isNotBlank()) user.displayName.take(2).uppercase() else "US"
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color(0xFF0F172A),
                                            border = BorderStroke(1.dp, SafetyBlue.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectAccountAction(user)
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
                                                    onClick = { selectAccountAction(user) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = SafetyBlue.copy(alpha = 0.25f),
                                                        contentColor = SafetyBlue
                                                    )
                                                ) {
                                                    Text(if (isId) "Pilih" else "Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

            // Database Apps Script Setup (Admin Password Protected)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.78f),
                border = BorderStroke(
                    1.dp,
                    if (isGasEnabled) SafetyTeal.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatabaseSetupDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isGasEnabled) SafetyTeal.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDatabaseAdminUnlocked) Icons.Default.LockOpen else Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (isGasEnabled) SafetyTeal else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isId) "Setup Database Apps Script (Khusus Admin)" else "Apps Script Database Setup (Admin Only)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isGasEnabled)
                                (if (isId) "Status: Aktif • Terhubung ke sheet 'user' & Firestore" else "Status: Active • Linked to 'user' sheet & Firestore")
                            else
                                (if (isId) "Status: Nonaktif • Ketuk untuk atur link /exec" else "Status: Disabled • Tap to configure /exec link"),
                            fontSize = 10.sp,
                            color = if (isGasEnabled) SafetyTeal else Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = if (isDatabaseAdminUnlocked) Icons.Default.Settings else Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
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

    if (showDatabaseSetupDialog) {
        DatabaseSetupAdminDialog(
            language = language,
            currentUrl = scriptUrl,
            currentEnabled = isGasEnabled,
            isAlreadyUnlocked = isDatabaseAdminUnlocked,
            onUnlock = onUnlockDatabaseAdmin,
            onSaveAndTest = onSaveAndTestDatabaseSetup,
            onDismiss = { showDatabaseSetupDialog = false }
        )
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

@Composable
private fun DatabaseSetupAdminDialog(
    language: AppLanguage,
    currentUrl: String,
    currentEnabled: Boolean,
    isAlreadyUnlocked: Boolean,
    onUnlock: (String) -> Boolean,
    onSaveAndTest: (
        adminPassword: String,
        newScriptUrl: String,
        gasEnabled: Boolean,
        publishGlobal: Boolean,
        onResult: (Boolean, String) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val isId = language == AppLanguage.ID
    val context = LocalContext.current

    var unlocked by remember(isAlreadyUnlocked) { mutableStateOf(isAlreadyUnlocked) }
    var adminPassInput by remember { mutableStateOf("") }
    var showAdminPass by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf<String?>(null) }

    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    var enabledInput by remember(currentEnabled) { mutableStateOf(currentEnabled) }
    var publishGlobal by remember { mutableStateOf(true) }
    var isTesting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var resultSuccess by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(0.98f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = (if (unlocked) SafetyTeal else SafetyBlue).copy(alpha = 0.14f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (unlocked) Icons.Default.LockOpen else Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (unlocked) SafetyTeal else SafetyBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isId) "Setup Database Apps Script" else "Apps Script Database Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (unlocked)
                                (if (isId) "Mode Admin Terbuka • Siap atur link /exec" else "Admin Mode Unlocked • Ready to configure")
                            else
                                (if (isId) "Terkunci • Khusus Admin Database" else "Locked • Database Admin Only"),
                            fontSize = 11.sp,
                            color = if (unlocked) SafetyTeal else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                if (!unlocked) {
                    Text(
                        text = if (isId)
                            "Masukkan kata sandi Admin untuk membuka pengaturan link Google Apps Script agar konfigurasi database tidak diubah sembarangan."
                        else
                            "Enter the Admin password to unlock Google Apps Script URL settings.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = adminPassInput,
                        onValueChange = {
                            adminPassInput = it
                            unlockError = null
                        },
                        label = { Text(if (isId) "Kata Sandi Admin" else "Admin Password") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = SafetyBlue) },
                        trailingIcon = {
                            IconButton(onClick = { showAdminPass = !showAdminPass }) {
                                Icon(
                                    imageVector = if (showAdminPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (showAdminPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        isError = unlockError != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!unlockError.isNullOrBlank()) {
                        Text(
                            text = unlockError!!,
                            fontSize = 11.sp,
                            color = DangerRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(if (isId) "Batal" else "Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (onUnlock(adminPassInput)) {
                                    unlocked = true
                                    unlockError = null
                                } else {
                                    unlockError = if (isId) "Kata sandi Admin salah." else "Incorrect Admin password."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isId) "Buka Kunci" else "Unlock")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isId) "Gunakan Login & Sync Apps Script" else "Enable Apps Script Auth & Sync",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isId) "Verifikasi login & register ke sheet 'user'" else "Verify login & register against 'user' sheet",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enabledInput, onCheckedChange = { enabledInput = it })
                    }

                    if (enabledInput) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text(if (isId) "URL Web App Apps Script (/exec)" else "Apps Script Web App URL (/exec)") },
                            placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val pasted = cb.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                                    if (pasted.isNotBlank()) urlInput = pasted
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isId) "Tempel URL" else "Paste URL", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { urlInput = GoogleAppsScriptSnippet.DEFAULT_WEBHOOK_URL },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isId) "Reset Default" else "Reset Default", fontSize = 11.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { publishGlobal = !publishGlobal },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = publishGlobal, onCheckedChange = { publishGlobal = it })
                            Text(
                                text = if (isId) "Sinkronkan ke Config Global Firestore (jika diizinkan)" else "Sync to Global Firestore Config (if permitted)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!resultMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = (if (resultSuccess) SafetyTeal else DangerRed).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, (if (resultSuccess) SafetyTeal else DangerRed).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = resultMessage!!,
                                fontSize = 11.sp,
                                color = if (resultSuccess) SafetyTeal else DangerRed,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            isTesting = true
                            resultMessage = null
                            onSaveAndTest(adminPassInput, urlInput, enabledInput, publishGlobal) { ok, msg ->
                                isTesting = false
                                resultSuccess = ok
                                resultMessage = msg
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isId) "Menghubungkan & Memuat User..." else "Connecting & Loading Users...")
                        } else {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isId) "Simpan, Tes & Muat Daftar User" else "Save, Test & Load Users",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (resultSuccess) {
                        FilledTonalButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isId) "Selesai & Lanjut Login" else "Done & Continue to Login", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
