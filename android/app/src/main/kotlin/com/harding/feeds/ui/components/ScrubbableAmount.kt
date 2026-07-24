package com.harding.feeds.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.harding.feeds.ui.formatAmount

/**
 * ScrubbableTime's sibling for a bottle amount: displays "90 ml" adjusted by the same
 * horizontal scrub-with-detents gesture, tap to type an exact value. The amount is
 * optional - scrubbing down to zero (or clearing the dialog) yields null, shown as a
 * dimmed "no amount"; one mechanism covers both "clear" and "unknown".
 */
@Composable
fun ScrubbableAmount(
    amountMl: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    stepMl: Int = 10,
    maxMl: Int = 500,
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(amountMl)
    val onAmountChange by rememberUpdatedState(onChange)
    val detentPx = with(LocalDensity.current) { DETENT_WIDTH_DP.dp.toPx() }
    var showTypeDialog by remember { mutableStateOf(false) }

    Text(
        text = current?.let(::formatAmount) ?: "no amount",
        style = style,
        textDecoration = TextDecoration.Underline,
        color = if (current != null) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { showTypeDialog = true }
            .scrubDetents(detentPx, haptics) { detents ->
                val next = ((current ?: 0) + detents * stepMl).coerceIn(0, maxMl)
                onAmountChange(if (next == 0) null else next)
            }
            .padding(horizontal = 4.dp),
    )

    if (showTypeDialog) {
        TypeAmountDialog(
            initialText = current?.toString() ?: "",
            maxMl = maxMl,
            onDismiss = { showTypeDialog = false },
            onConfirm = { typed ->
                showTypeDialog = false
                onAmountChange(typed)
            },
        )
    }
}

@Composable
internal fun TypeAmountDialog(
    initialText: String,
    maxMl: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    val trimmed = text.trim()
    val parsed = trimmed.toIntOrNull()
    val valid = trimmed.isEmpty() || (parsed != null && parsed in 0..maxMl)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set amount") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("ml - blank for none") },
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsed?.takeIf { it > 0 }) },
                enabled = valid,
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
