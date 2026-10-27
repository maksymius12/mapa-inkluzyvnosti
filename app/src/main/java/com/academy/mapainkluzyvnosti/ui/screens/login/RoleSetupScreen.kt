package com.academy.mapainkluzyvnosti.ui.screens.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.ReferralSourceSelector
import com.academy.mapainkluzyvnosti.ui.components.RoleAndAgeSelector
import org.koin.compose.viewmodel.koinViewModel

/** Перший вхід через Google: один раз обрати мету й вікову категорію, далі не питаємо. */
@Composable
fun RoleSetupScreen(
    onDone: () -> Unit,
    onNeedsSignIn: () -> Unit,
    viewModel: RoleSetupViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isDone) { if (uiState.isDone) onDone() }
    LaunchedEffect(uiState.needsSignIn) { if (uiState.needsSignIn) onNeedsSignIn() }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        AuthHeader(subtitle = "Ще один крок", logoWidth = 56.dp, modifier = Modifier.padding(bottom = 24.dp))
        Text(
            text = if (uiState.name.isNotBlank()) "Вітаємо, ${uiState.name}!" else "Вітаємо!",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Розкажіть трохи про себе — від цього залежать можливості в застосунку. Питаємо лише раз, змінити можна в Налаштуваннях.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )
        RoleAndAgeSelector(
            purposeRole = uiState.purposeRole,
            onPurposeRoleChange = viewModel::onPurposeRoleChange,
            ageGroup = uiState.ageGroup,
            onAgeGroupChange = viewModel::onAgeGroupChange,
            enabled = !uiState.isSaving
        )
        ReferralSourceSelector(
            value = uiState.referralSource,
            onChange = viewModel::onReferralSourceChange,
            enabled = !uiState.isSaving,
            modifier = Modifier.padding(top = 20.dp)
        )
        PrimaryButton(
            text = "Продовжити",
            onClick = viewModel::save,
            loading = uiState.isSaving,
            modifier = Modifier.padding(top = 28.dp)
        )
        uiState.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
    }
}
