package com.academy.mapainkluzyvnosti.ui.screens.login

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.OtpInput
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.ScreenHeader
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * «Введіть код»: підтвердження реєстрації або скидання пароля 6-значним кодом із листа.
 * Для скидання після коду показується «Новий пароль».
 */
@Composable
fun VerifyCodeScreen(
    purpose: OtpPurpose,
    email: String,
    sendCodeOnStart: Boolean,
    onSignedIn: () -> Unit,
    onNeedsRoleSetup: () -> Unit,
    onChangeEmail: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: OtpViewModel = koinViewModel(parameters = { parametersOf(purpose, email, sendCodeOnStart) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.passwordChanged) {
        if (uiState.passwordChanged) {
            Toast.makeText(context, "Пароль змінено", Toast.LENGTH_SHORT).show()
            viewModel.consumePasswordChanged()
        }
    }

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            AuthDestination.MAP -> onSignedIn()
            AuthDestination.ROLE_SETUP -> onNeedsRoleSetup()
            null -> return@LaunchedEffect
        }
        viewModel.consumeDestination()
    }

    fun goBack() {
        when {
            uiState.stage == OtpStage.NEW_PASSWORD -> viewModel.abandonPasswordReset(onBackToLogin)
            purpose == OtpPurpose.SIGN_UP -> onChangeEmail()
            else -> onBackToLogin()
        }
    }
    BackHandler(onBack = ::goBack)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = when {
                uiState.stage == OtpStage.NEW_PASSWORD -> "Новий пароль"
                purpose == OtpPurpose.SIGN_UP -> "Підтвердження пошти"
                else -> "Відновлення пароля"
            },
            onBack = ::goBack
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            when (uiState.stage) {
                OtpStage.ENTER_CODE -> EnterCodeContent(uiState, viewModel, onChangeEmail, onBackToLogin)
                OtpStage.NEW_PASSWORD -> NewPasswordContent(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun EnterCodeContent(
    uiState: OtpUiState,
    viewModel: OtpViewModel,
    onChangeEmail: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Icon(
            Icons.Outlined.MarkEmailUnread,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "Введіть код",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = when (uiState.purpose) {
                OtpPurpose.SIGN_UP -> "Ми надіслали 6-значний код на ${uiState.email}"
                // Не розкриваємо, чи існує акаунт із такою поштою.
                OtpPurpose.PASSWORD_RESET -> "Якщо акаунт із поштою ${uiState.email} існує, ми надіслали 6-значний код"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "Код діє 1 годину. Не бачите листа? Перевірте папку «Спам».",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    OtpInput(
        value = uiState.code,
        onValueChange = viewModel::onCodeChange,
        onComplete = viewModel::onCodeComplete,
        enabled = !uiState.isVerifying,
        isError = uiState.codeError != null,
        modifier = Modifier.padding(top = 24.dp)
    )

    uiState.codeError?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
    }
    uiState.infoMessage?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 24.dp)) {
        PrimaryButton(
            text = if (uiState.lockSeconds > 0) "Забагато спроб — зачекайте ${uiState.lockSeconds} с" else "Підтвердити",
            onClick = viewModel::verify,
            enabled = uiState.canVerify,
            loading = uiState.isVerifying
        )
        SecondaryButton(
            text = if (uiState.resendCooldownSeconds > 0) {
                "Надіслати код ще раз (${uiState.resendCooldownSeconds} с)"
            } else {
                "Надіслати код ще раз"
            },
            onClick = viewModel::resend,
            enabled = uiState.canResend
        )
    }

    TextButton(
        onClick = if (uiState.purpose == OtpPurpose.SIGN_UP) onChangeEmail else onBackToLogin,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Text(if (uiState.purpose == OtpPurpose.SIGN_UP) "Змінити пошту" else "Повернутися до входу")
    }
}

@Composable
private fun NewPasswordContent(uiState: OtpUiState, viewModel: OtpViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Icon(
            Icons.Outlined.LockReset,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "Придумайте новий пароль",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Щонайменше $PASSWORD_MIN_LENGTH символів. Після збереження ви одразу ввійдете.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
    }
    PasswordField(
        value = uiState.newPassword,
        onValueChange = viewModel::onNewPasswordChange,
        label = "Новий пароль",
        error = uiState.newPasswordError,
        enabled = !uiState.isSavingPassword,
        imeAction = ImeAction.Next
    )
    PasswordField(
        value = uiState.newPasswordConfirm,
        onValueChange = viewModel::onNewPasswordConfirmChange,
        label = "Підтвердження пароля",
        error = uiState.newPasswordConfirmError,
        enabled = !uiState.isSavingPassword,
        modifier = Modifier.padding(top = 8.dp)
    )
    PrimaryButton(
        text = "Зберегти пароль",
        onClick = viewModel::saveNewPassword,
        loading = uiState.isSavingPassword,
        modifier = Modifier.padding(top = 20.dp)
    )
    AuthErrorText(uiState.passwordSaveError)
}
