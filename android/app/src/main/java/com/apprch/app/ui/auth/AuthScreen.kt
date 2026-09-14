package com.apprch.app.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.apprch.app.data.GroupStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen() {
    var showReset by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }

    AnimatedContent(
        targetState = showReset,
        transitionSpec = {
            if (targetState) {
                slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
            } else {
                slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
            }
        },
        label = "auth-reset"
    ) { reset ->
        if (reset) {
            ResetPasswordScreen(
                initialEmail = email,
                onEmailChange = { email = it },
                onBack = { showReset = false }
            )
        } else {
            SignInScreen(
                email = email,
                onEmailChange = { email = it },
                onForgotPassword = { showReset = true }
            )
        }
    }
}

@Composable
private fun SignInScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    onForgotPassword: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var isSignUp by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val formValid = email.isNotBlank() && password.length >= 6 && (!isSignUp || displayName.isNotBlank())
    val emailComplete = AuthEmail.isComplete(email)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Apprch", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Tap once. Everyone knows.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))

        if (isSignUp) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        if (isSignUp) {
                            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                            val user = result.user
                            if (user != null) {
                                user.updateProfile(
                                    UserProfileChangeRequest.Builder()
                                        .setDisplayName(displayName.trim())
                                        .build()
                                ).await()
                                db.collection("users").document(user.uid)
                                    .set(
                                        GroupStore.directoryFields(displayName.trim(), email.trim()),
                                        SetOptions.merge()
                                    )
                                    .await()
                            }
                        } else {
                            auth.signInWithEmailAndPassword(email.trim(), password).await()
                        }
                    } catch (e: Exception) {
                        errorMessage = AuthErrors.userFacingMessage(e)
                    }
                    isLoading = false
                }
            },
            enabled = formValid && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (isSignUp) "Create account" else "Sign in")
        }

        if (!isSignUp) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onForgotPassword,
                enabled = emailComplete
            ) {
                Text("Forgot Password")
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = {
            isSignUp = !isSignUp
            errorMessage = null
        }) {
            Text(if (isSignUp) "Already have an account? Sign in" else "New here? Create account")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResetPasswordScreen(
    initialEmail: String,
    onEmailChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resetSent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        TopAppBar(
            title = { Text("Reset password") },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("Back") }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Provide the email on your account. We’ll send a reset link there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    onEmailChange(it)
                    resetSent = false
                    errorMessage = null
                },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        resetSent = false
                        try {
                            auth.sendPasswordResetEmail(email.trim()).await()
                            resetSent = true
                        } catch (_: FirebaseAuthInvalidUserException) {
                            resetSent = true
                        } catch (e: Exception) {
                            errorMessage = AuthErrors.userFacingMessage(e)
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading && AuthEmail.isComplete(email),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Reset password")
            }
            if (resetSent) {
                Text(
                    "Check your email for a reset link.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private object AuthEmail {
    fun isComplete(value: String): Boolean {
        val trimmed = value.trim()
        val parts = trimmed.split("@")
        if (parts.size != 2 || parts[0].isEmpty()) return false
        val domain = parts[1]
        return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.') && !domain.contains(' ')
    }
}
