package com.harding.feeds.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.harding.feeds.ui.TIME_FORMAT
import com.harding.feeds.ui.parseTypedTime
import com.harding.feeds.ui.toLocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The SPEC's no-time-picker time control: displays HH:mm, adjusted by a horizontal scrub
 * with a haptic detent per 1-minute step; tapping it opens a type-it-in dialog as the
 * fallback. The scrub sits on the text itself, so it wins over any horizontal gesture
 * (e.g. side-swipe) on an enclosing surface.
 */
@Composable
fun ScrubbableTime(
    time: Instant,
    onTimeChange: (Instant) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(time)
    val onChange by rememberUpdatedState(onTimeChange)
    val detentPx = with(LocalDensity.current) { DETENT_WIDTH_DP.dp.toPx() }
    var showTypeDialog by remember { mutableStateOf(false) }

    Text(
        text = current.toLocalTime(zone).format(TIME_FORMAT),
        style = style,
        // Underline signals "this is adjustable" without extra chrome.
        textDecoration = TextDecoration.Underline,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { showTypeDialog = true }
            .pointerInput(Unit) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    accumulated += dragAmount
                    val detents = (accumulated / detentPx).toInt()
                    if (detents != 0) {
                        accumulated -= detents * detentPx
                        repeat(abs(detents)) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onChange(current.plus(detents.toLong(), ChronoUnit.MINUTES))
                    }
                }
            }
            .padding(horizontal = 4.dp),
    )

    if (showTypeDialog) {
        TypeTimeDialog(
            initialText = current.toLocalTime(zone).format(TIME_FORMAT),
            onDismiss = { showTypeDialog = false },
            onConfirm = { typed ->
                showTypeDialog = false
                onChange(current.atZone(zone).with(typed).toInstant())
            },
        )
    }
}

@Composable
internal fun TypeTimeDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (java.time.LocalTime) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    val parsed = parseTypedTime(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set time") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("HH:mm") },
                isError = text.isNotBlank() && parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null,
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val DETENT_WIDTH_DP = 18
