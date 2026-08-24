package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppLanguage
import com.example.data.AppStrings
import com.example.ui.theme.*

enum class AuthMode {
    LOGIN, REGISTER, FORGOT_PASSWORD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthDialog(
    language: AppLanguage,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onLogin: (email: String, pass: String) -> Unit,
    onRegister: (email: String, pass: String, name: String, company: String, role: String, cert: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }

    // Form fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("HSSE Tech Operations") }
    var role by remember { mutableStateOf("Lead HSE Inspector") }
    var certification by remember { mutableStateOf("NEBOSH / AK3 Umum") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current

    // Clear validation error on tab switch
    LaunchedEffect(mode) {
        validationError = null
        onClearError()
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SafetyBlue.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (mode) {
                                        AuthMode.LOGIN -> Icons.Default.Lock
                                        AuthMode.REGISTER -> Icons.Default.PersonAdd
                                        AuthMode.FORGOT_PASSWORD -> Icons.Default.LockReset
                                    },
                                    contentDescription = null,
                                    tint = SafetyBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = when (mode) {
                                    AuthMode.LOGIN -> AppStrings.get("auth_login_tab", language)
                                    AuthMode.REGISTER -> AppStrings.get("auth_register_tab", language)
                                    AuthMode.FORGOT_PASSWORD -> AppStrings.get("auth_reset_tab", language)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Firebase Cloud Auth • IOGP & OSHA",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Tab Switcher (Login vs Register vs Forgot)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (mode == AuthMode.LOGIN) SafetyBlue else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isLoading) mode = AuthMode.LOGIN
                                }
                        ) {
                            Text(
                                text = AppStrings.get("auth_login_tab", language),
                                fontSize = 12.sp,
                                fontWeight = if (mode == AuthMode.LOGIN) FontWeight.Bold else FontWeight.Normal,
                                color = if (mode == AuthMode.LOGIN) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (mode == AuthMode.REGISTER) SafetyBlue else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isLoading) mode = AuthMode.REGISTER
                                }
                        ) {
                            Text(
                                text = AppStrings.get("auth_register_tab", language),
                                fontSize = 12.sp,
                                fontWeight = if (mode == AuthMode.REGISTER) FontWeight.Bold else FontWeight.Normal,
                                color = if (mode == AuthMode.REGISTER) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                // Error Banner (Validation or Firebase Error)
                val activeError = validationError ?: errorMessage
                if (activeError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DangerRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                text = activeError,
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRed,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Subtitle description
                Text(
                    text = AppStrings.get("auth_subtitle", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                // FORM INPUTS
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Registration extra fields: Full Name
                    if (mode == AuthMode.REGISTER) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                validationError = null
                            },
                            label = { Text(AppStrings.get("full_name_label", language)) },
                            placeholder = { Text(AppStrings.get("full_name_placeholder", language)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = SafetyBlue) },
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            validationError = null
                            onClearError()
                        },
                        label = { Text(AppStrings.get("email_label", language)) },
                        placeholder = { Text(AppStrings.get("email_placeholder", language)) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = SafetyBlue) },
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (mode == AuthMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = { focusManager.clearFocus() }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password Field (For Login & Register)
                    if (mode != AuthMode.FORGOT_PASSWORD) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                validationError = null
                                onClearError()
                            },
                            label = { Text(AppStrings.get("password_label", language)) },
                            placeholder = { Text(AppStrings.get("password_placeholder", language)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SafetyBlue) },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (mode == AuthMode.LOGIN) ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                onDone = { focusManager.clearFocus() }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Registration Extra Fields: Confirm Password, Company, Role, Certification
                    if (mode == AuthMode.REGISTER) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                validationError = null
                            },
                            label = { Text(AppStrings.get("confirm_password_label", language)) },
                            placeholder = { Text(AppStrings.get("confirm_password_placeholder", language)) },
                            leadingIcon = { Icon(Icons.Default.LockClock, contentDescription = null, tint = SafetyBlue) },
                            trailingIcon = {
                                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = company,
                            onValueChange = { company = it },
                            label = { Text(AppStrings.get("company_auth_label", language)) },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = SafetyBlue) },
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = role,
                            onValueChange = { role = it },
                            label = { Text(AppStrings.get("role_auth_label", language)) },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = SafetyBlue) },
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = certification,
                            onValueChange = { certification = it },
                            label = { Text(AppStrings.get("cert_auth_label", language)) },
                            leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null, tint = SafetyTeal) },
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Forgot Password link (when on Login)
                if (mode == AuthMode.LOGIN) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                if (!isLoading) mode = AuthMode.FORGOT_PASSWORD
                            }
                        ) {
                            Text(
                                text = AppStrings.get("forgot_password_link", language),
                                fontSize = 12.sp,
                                color = SafetyBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Back to login link (when on Forgot Password)
                if (mode == AuthMode.FORGOT_PASSWORD) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TextButton(
                            onClick = {
                                if (!isLoading) mode = AuthMode.LOGIN
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = AppStrings.get("back_to_login", language),
                                fontSize = 12.sp,
                                color = SafetyBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // MAIN ACTION BUTTON
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        when (mode) {
                            AuthMode.LOGIN -> {
                                if (email.isBlank() || !email.contains("@")) {
                                    validationError = AppStrings.get("email_invalid", language)
                                } else if (password.length < 6) {
                                    validationError = AppStrings.get("password_too_short", language)
                                } else {
                                    onLogin(email, password)
                                }
                            }
                            AuthMode.REGISTER -> {
                                if (displayName.isBlank()) {
                                    validationError = AppStrings.get("name_required", language)
                                } else if (email.isBlank() || !email.contains("@")) {
                                    validationError = AppStrings.get("email_invalid", language)
                                } else if (password.length < 6) {
                                    validationError = AppStrings.get("password_too_short", language)
                                } else if (password != confirmPassword) {
                                    validationError = AppStrings.get("password_mismatch", language)
                                } else {
                                    onRegister(email, password, displayName, company, role, certification)
                                }
                            }
                            AuthMode.FORGOT_PASSWORD -> {
                                if (email.isBlank() || !email.contains("@")) {
                                    validationError = AppStrings.get("email_invalid", language)
                                } else {
                                    onResetPassword(email)
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Memproses...", fontWeight = FontWeight.Bold)
                    } else {
                        val btnText = when (mode) {
                            AuthMode.LOGIN -> AppStrings.get("btn_login", language)
                            AuthMode.REGISTER -> AppStrings.get("btn_register", language)
                            AuthMode.FORGOT_PASSWORD -> AppStrings.get("btn_reset_password", language)
                        }
                        Icon(
                            imageVector = when (mode) {
                                AuthMode.LOGIN -> Icons.Default.Login
                                AuthMode.REGISTER -> Icons.Default.CheckCircle
                                AuthMode.FORGOT_PASSWORD -> Icons.Default.Send
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(btnText, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }

                // Offline Guest Mode button
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = AppStrings.get("guest_mode_btn", language),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Security footer
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = SafetyTeal, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Dilindungi oleh Firebase Authentication & enkripsi TLS tingkat enterprise untuk kepatuhan audit K3.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
