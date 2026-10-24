package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val OTP_LENGTH = 6

/** Лише цифри з тексту (вставка «123 456», «123-456»), не більше [length]. */
fun normalizeOtp(text: String, length: Int = OTP_LENGTH): String = text.filter(Char::isDigit).take(length)

/**
 * Поле коду з листа: [length] клітинок. Під капотом — одне текстове поле, тож перехід між
 * клітинками, стирання й вставка всього коду з буфера працюють як у звичайному полі;
 * клавіатура цифрова, ОС може запропонувати код з SMS/пошти (autofill ContentType.SmsOtpCode).
 */
@Composable
fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = OTP_LENGTH,
    enabled: Boolean = true,
    isError: Boolean = false,
    autoFocus: Boolean = true,
    onComplete: (String) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }

    // Курсор завжди в кінці — нову цифру не вставити посередині.
    val fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))

    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            val digits = normalizeOtp(newValue.text, length)
            if (digits != value) {
                onValueChange(digits)
                if (digits.length == length) onComplete(digits)
            }
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.length == length) onComplete(value) }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics {
                contentType = ContentType.SmsOtpCode
                contentDescription = "Код підтвердження з $length цифр"
            },
        decorationBox = { innerTextField ->
            Box {
                // Справжнє поле невидиме: показуємо лише клітинки, але жести (вставка) лишаються.
                Box(modifier = Modifier.alpha(0f)) { innerTextField() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(length) { index ->
                        OtpCell(
                            char = value.getOrNull(index),
                            isActive = enabled && index == value.length.coerceAtMost(length - 1) && value.length < length,
                            isError = isError,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun OtpCell(char: Char?, isActive: Boolean, isError: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(0.82f)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(if (isActive || isError) 2.dp else 1.dp, borderColor, shape)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        if (char != null) {
            Text(text = char.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        } else if (isActive) {
            BlinkingCaret()
        }
    }
}

@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "otp-caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 530), RepeatMode.Reverse),
        label = "otp-caret-alpha"
    )
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(24.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary)
    )
}
