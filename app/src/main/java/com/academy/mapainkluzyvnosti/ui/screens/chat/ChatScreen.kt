package com.academy.mapainkluzyvnosti.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.academy.mapainkluzyvnosti.data.model.ChatMessage
import com.academy.mapainkluzyvnosti.ui.components.PrimaryButton
import com.academy.mapainkluzyvnosti.ui.components.ScreenHeader
import com.academy.mapainkluzyvnosti.ui.components.SecondaryButton
import org.koin.compose.viewmodel.koinViewModel
import java.time.ZonedDateTime

/** Спільний чат для всіх, хто увійшов: лише текст до 500 символів, нові повідомлення внизу. */
@Composable
fun ChatScreen(onBack: () -> Unit, onRequestLogin: () -> Unit, viewModel: ChatViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // Опитування лише поки екран видимий: згорнули застосунок — запити зупиняються.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, state.isGuest) {
        if (!state.isGuest) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.pollWhileVisible() }
        }
    }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.noticeShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            ScreenHeader(title = "Чат", onBack = onBack)

            if (state.isGuest) {
                GuestPrompt(onLogin = {
                    viewModel.leaveGuestMode()
                    onRequestLogin()
                })
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.isLoading && state.messages.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        state.messages.isEmpty() && state.loadError != null -> CenteredMessage(
                            title = "Чат не завантажився",
                            text = "${state.loadError}. Повторимо автоматично за кілька секунд.",
                            action = { SecondaryButton(text = "Спробувати зараз", onClick = viewModel::retry) }
                        )
                        state.messages.isEmpty() -> CenteredMessage(
                            title = "Тут поки тихо",
                            text = "Напишіть перше повідомлення. Його побачать усі, хто увійшов у застосунок."
                        )
                        else -> MessageList(
                            messages = state.messages,
                            isAdmin = state.isAdmin,
                            deletingIds = state.deletingIds,
                            sentCount = state.sentCount,
                            onDelete = { pendingDelete = it }
                        )
                    }
                    // Повідомлення вже є, а оновити не вдалося — показуємо старі й кажемо чому.
                    if (state.loadError != null && state.messages.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        ) {
                            Text(
                                text = "${state.loadError}. Показано останні завантажені повідомлення.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ChatInput(
                    draft = state.draft,
                    problem = state.draftProblem,
                    canSend = state.canSend,
                    isSending = state.isSending,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 72.dp)
        )
    }

    pendingDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Видалити повідомлення?") },
            text = { Text(if (message.isMine) "Його більше ніхто не побачить." else "Повідомлення від «${message.authorName}» зникне для всіх.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(message.id)
                    pendingDelete = null
                }) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isAdmin: Boolean,
    deletingIds: Set<String>,
    sentCount: Int,
    onDelete: (ChatMessage) -> Unit
) {
    // reverseLayout: список «росте» знизу, найновіше — завжди біля поля вводу.
    val listState = rememberLazyListState()
    val newestId = messages.lastOrNull()?.id
    // Прийшло нове повідомлення, а людина внизу списку — показуємо його (інакше лишаємо, де читає).
    LaunchedEffect(newestId) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    LaunchedEffect(sentCount) {
        if (sentCount > 0) listState.animateScrollToItem(0)
    }
    val now = ZonedDateTime.now()

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
    ) {
        items(messages.asReversed(), key = { it.id }) { message ->
            MessageBubble(
                message = message,
                timeText = formatChatTime(message.createdAt, now),
                canDelete = canDeleteChatMessage(message.isMine, isAdmin),
                isDeleting = message.id in deletingIds,
                onDelete = { onDelete(message) }
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    timeText: String?,
    canDelete: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit
) {
    val mine = message.isMine
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp).semantics(mergeDescendants = true) {}
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = if (mine) "Ви" else message.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
                timeText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    )
                }
            }
        }
        if (canDelete) {
            TextButton(
                onClick = onDelete,
                enabled = !isDeleting,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (isDeleting) "Видаляємо…" else "Видалити",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ChatInput(
    draft: String,
    problem: ChatTextProblem?,
    canSend: Boolean,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val tooLong = problem == ChatTextProblem.TOO_LONG
    val counter = chatCounterText(draft)
    val hint: String? = if (tooLong) "Задовге: $counter. Максимум $CHAT_MAX_LENGTH символів" else counter
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Повідомлення для всіх") },
            maxLines = 5,
            isError = tooLong,
            enabled = !isSending,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = if (hint != null) { { Text(hint) } } else null
        )
        IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.padding(start = 4.dp)) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Надіслати",
                    tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GuestPrompt(onLogin: () -> Unit) {
    CenteredMessage(
        title = "Увійдіть, щоб писати",
        text = "Чат доступний лише тим, хто увійшов: так менше спаму й видно, хто пише.",
        action = { PrimaryButton(text = "Увійти", onClick = onLogin) }
    )
}

@Composable
private fun CenteredMessage(title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            action?.let {
                Box(modifier = Modifier.padding(top = 20.dp)) { it() }
            }
        }
    }
}
