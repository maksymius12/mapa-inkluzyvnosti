package com.academy.mapainkluzyvnosti.ui.screens.login

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.academy.mapainkluzyvnosti.BuildConfig
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** Вхід: email і пароль, «Забули пароль?», Google, гість і посилання на реєстрацію. */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onNeedsRoleSetup: () -> Unit,
    onOpenSignUp: () -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            AuthDestination.MAP -> onLoggedIn()
            AuthDestination.ROLE_SETUP -> onNeedsRoleSetup()
            null -> return@LaunchedEffect
        }
        viewModel.consumeDestination()
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeader(subtitle = "Шевченківський район", modifier = Modifier.padding(bottom = 28.dp))

        Text(text = "Вхід", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            enabled = !uiState.isLoading,
            isError = uiState.emailError != null,
            supportingText = uiState.emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            shape = AuthFieldShape,
            modifier = Modifier.fillMaxWidth()
        )

        PasswordField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Пароль",
            error = uiState.passwordError,
            enabled = !uiState.isLoading,
            modifier = Modifier.padding(top = 8.dp)
        )

        TextButton(
            onClick = viewModel::openForgotPasswordDialog,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Забули пароль?")
        }

        PrimaryButton(
            text = "Увійти",
            onClick = viewModel::submitEmailForm,
            loading = uiState.isLoading,
            modifier = Modifier.padding(top = 8.dp)
        )

        AuthErrorText(uiState.errorMessage)

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

        SecondaryButton(
            text = "Увійти через Google",
            onClick = ::launchGoogleSignIn,
            enabled = !uiState.isLoading
        )

        TextButton(
            onClick = { viewModel.continueAsGuest() },
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Продовжити як гість", fontWeight = FontWeight.SemiBold)
        }

        TextButton(
            onClick = onOpenSignUp,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Немає акаунта? Зареєструватись")
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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
            Column {
                Text(
                    text = "Ми надішлемо лист із посиланням для зміни пароля.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !isSending && isValidEmail(email)) {
                Text("Надіслати")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Скасувати") }
        }
    )
}
