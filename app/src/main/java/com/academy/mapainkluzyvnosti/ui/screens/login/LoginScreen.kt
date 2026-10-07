package com.academy.mapainkluzyvnosti.ui.screens.login

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.academy.mapainkluzyvnosti.BuildConfig
import com.academy.mapainkluzyvnosti.data.model.UserAgeGroup
import com.academy.mapainkluzyvnosti.data.model.UserPurposeRole
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoggedIn()
    }

    LaunchedEffect(uiState.resetPasswordMessage) {
        uiState.resetPasswordMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeResetPasswordMessage()
        }
    }

    fun launchGoogleSignIn() {
        scope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.signInWithGoogleIdToken(googleIdTokenCredential.idToken)
                }
            } catch (e: GetCredentialException) {
                // Користувач скасував вибір акаунта — нічого не робимо.
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Мапа Інклюзивності",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Карта доступності Шевченківського району Києва",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 32.dp)
        )

        Text(text = "Мета використання", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp)) {
            SegmentedButton(
                selected = uiState.purposeRole == UserPurposeRole.RESIDENT,
                onClick = { viewModel.setPurposeRole(UserPurposeRole.RESIDENT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Мешканець") }
            SegmentedButton(
                selected = uiState.purposeRole == UserPurposeRole.VOLUNTEER,
                onClick = { viewModel.setPurposeRole(UserPurposeRole.VOLUNTEER) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Волонтер") }
        }

        Text(text = "Вікова категорія", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp)) {
            SegmentedButton(
                selected = uiState.ageGroup == UserAgeGroup.STUDENT,
                onClick = { viewModel.setAgeGroup(UserAgeGroup.STUDENT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Учень") }
            SegmentedButton(
                selected = uiState.ageGroup == UserAgeGroup.ADULT,
                onClick = { viewModel.setAgeGroup(UserAgeGroup.ADULT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Дорослий") }
        }

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            isError = uiState.emailError != null,
            supportingText = uiState.emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Пароль") },
            singleLine = true,
            isError = uiState.passwordError != null,
            supportingText = uiState.passwordError?.let { { Text(it) } },
            visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = viewModel::togglePasswordVisibility) {
                    Icon(
                        imageVector = if (uiState.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (uiState.isPasswordVisible) "Приховати пароль" else "Показати пароль"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        if (uiState.formMode == AuthFormMode.SIGN_IN) {
            TextButton(
                onClick = viewModel::openForgotPasswordDialog,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Забули пароль?")
            }
        }

        Button(
            onClick = viewModel::submitEmailForm,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(uiState.formTitle)
        }

        TextButton(
            onClick = {
                viewModel.setFormMode(if (uiState.formMode == AuthFormMode.SIGN_IN) AuthFormMode.SIGN_UP else AuthFormMode.SIGN_IN)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (uiState.formMode == AuthFormMode.SIGN_IN) "Немає акаунту? Зареєструватись" else "Вже є акаунт? Увійти"
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "  або  ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Button(
            onClick = ::launchGoogleSignIn,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Увійти за допомогою Google")
        }

        OutlinedButton(
            onClick = { viewModel.continueAsGuest() },
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text("Продовжити як гість")
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }
    }

    if (uiState.isForgotPasswordDialogOpen) {
        ForgotPasswordDialog(
            email = uiState.forgotPasswordEmail,
            isSending = uiState.isSendingResetEmail,
            onEmailChange = viewModel::onForgotPasswordEmailChange,
            onDismiss = viewModel::dismissForgotPasswordDialog,
            onSend = viewModel::sendPasswordReset
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    email: String,
    isSending: Boolean,
    onEmailChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Відновлення пароля") },
        text = {
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !isSending && email.contains("@")) {
                Text("Надіслати")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Скасувати") }
        }
    )
}
