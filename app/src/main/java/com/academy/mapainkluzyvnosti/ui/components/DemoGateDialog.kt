package com.academy.mapainkluzyvnosti.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Показується замість реального запиту в базу, коли дію намагається виконати гість (demo-режим). */
@Composable
fun DemoGateDialog(onDismiss: () -> Unit, onSignInWithGoogle: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Це демо-режим") },
        text = { Text("Щоб додавати дані, увійди через Google") },
        confirmButton = {
            TextButton(onClick = onSignInWithGoogle) { Text("Увійти через Google") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрити") }
        }
    )
}
