package com.academy.mapainkluzyvnosti.ui.screens.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.ReferralSourceSelector
import com.academy.mapainkluzyvnosti.ui.components.RoleAndAgeSelector
import com.academy.mapainkluzyvnosti.ui.components.ScreenHeader
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import org.koin.compose.viewmodel.koinViewModel

/** Реєстрація у два кроки з індикатором кроків; якщо треба підтвердити пошту — екран введення коду. */
@Composable
fun SignUpScreen(
    onSignedIn: () -> Unit,
    onCodeSent: (email: String) -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: SignUpViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) onSignedIn()
    }

    LaunchedEffect(uiState.codeSentTo) {
        uiState.codeSentTo?.let { email ->
            viewModel.consumeCodeSent()
            onCodeSent(email)
        }
    }

    fun goBack() {
        if (!viewModel.back()) onOpenLogin()
    }
    BackHandler(onBack = ::goBack)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Реєстрація", onBack = ::goBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            when (uiState.step) {
                SignUpStep.ACCOUNT -> AccountStep(uiState, viewModel)
                SignUpStep.ROLES -> RolesStep(uiState, viewModel)
            }

            TextButton(
                onClick = onOpenLogin,
                enabled = !uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text("Вже є акаунт? Увійти")
            }
        }
    }
}

@Composable
private fun AccountStep(uiState: SignUpUiState, viewModel: SignUpViewModel) {
    StepIndicator(step = 1, total = 2, modifier = Modifier.padding(bottom = 16.dp))
    Text(text = "Обліковий запис", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))

    OutlinedTextField(
        value = uiState.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Ім'я") },
        singleLine = true,
        isError = uiState.nameError != null,
        supportingText = uiState.nameError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        shape = AuthFieldShape,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = uiState.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        isError = uiState.emailError != null,
        supportingText = uiState.emailError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        shape = AuthFieldShape,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    PasswordField(
        value = uiState.password,
        onValueChange = viewModel::onPasswordChange,
        label = "Пароль",
        error = uiState.passwordError,
        imeAction = ImeAction.Next,
        modifier = Modifier.padding(top = 8.dp)
    )
    PasswordField(
        value = uiState.passwordConfirm,
        onValueChange = viewModel::onPasswordConfirmChange,
        label = "Підтвердження пароля",
        error = uiState.passwordConfirmError,
        modifier = Modifier.padding(top = 8.dp)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .toggleable(value = uiState.acceptedTerms, role = Role.Checkbox, onValueChange = viewModel::onTermsChange)
    ) {
        Checkbox(checked = uiState.acceptedTerms, onCheckedChange = null)
        Text(
            text = "Погоджуюся з умовами користування та обробкою моїх даних",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    uiState.termsError?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }

    PrimaryButton(text = "Далі", onClick = viewModel::next, modifier = Modifier.padding(top = 20.dp))
    AuthErrorText(uiState.errorMessage)
}

@Composable
private fun RolesStep(uiState: SignUpUiState, viewModel: SignUpViewModel) {
    StepIndicator(step = 2, total = 2, modifier = Modifier.padding(bottom = 16.dp))
    Text(text = "Про вас", style = MaterialTheme.typography.titleLarge)
    Text(
        text = "Від цього залежать можливості в застосунку. Змінити можна в Налаштуваннях.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
    )
    RoleAndAgeSelector(
        purposeRole = uiState.purposeRole,
        onPurposeRoleChange = viewModel::onPurposeRoleChange,
        ageGroup = uiState.ageGroup,
        onAgeGroupChange = viewModel::onAgeGroupChange,
        enabled = !uiState.isSubmitting
    )
    ReferralSourceSelector(
        value = uiState.referralSource,
        onChange = viewModel::onReferralSourceChange,
        enabled = !uiState.isSubmitting,
        modifier = Modifier.padding(top = 20.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 24.dp)) {
        PrimaryButton(text = "Зареєструватись", onClick = viewModel::submit, loading = uiState.isSubmitting)
        SecondaryButton(text = "Назад", onClick = { viewModel.back() }, enabled = !uiState.isSubmitting)
    }
    AuthErrorText(uiState.errorMessage)
}
