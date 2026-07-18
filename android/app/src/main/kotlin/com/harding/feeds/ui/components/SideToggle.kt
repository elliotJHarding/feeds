package com.harding.feeds.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harding.feeds.client.models.Side
import com.harding.feeds.ui.label
import com.harding.feeds.ui.onSideColor
import com.harding.feeds.ui.sideColor

/** Tappable L/R pair - the discoverable fallback for the swipe-to-pick-side gesture. */
@Composable
fun SideToggle(
    selected: Side?,
    onSelect: (Side) -> Unit,
    modifier: Modifier = Modifier,
    optionSize: Dp = 56.dp,
) {
    Row(modifier) {
        listOf(Side.l, Side.r).forEach { side ->
            val isSelected = side == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(optionSize)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) side.sideColor
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(side) },
            ) {
                Text(
                    text = side.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSelected) onSideColor
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
