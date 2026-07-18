package com.harding.feeds.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harding.feeds.client.models.Side
import com.harding.feeds.ui.onSideColor
import com.harding.feeds.ui.sideColor

/**
 * The side picker: a full-width pair of labelled buttons in the thumb zone. The selected side
 * fills with its own hue (moonlight L / candle amber R) so the choice reads without parsing a
 * letter. This is the discoverable control; a horizontal swipe on the entry surface is the
 * shortcut that does the same thing.
 */
@Composable
fun SideToggle(
    selected: Side?,
    onSelect: (Side) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SideButton(Side.l, "LEFT", selected == Side.l, onSelect, Modifier.weight(1f))
        SideButton(Side.r, "RIGHT", selected == Side.r, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun SideButton(
    side: Side,
    label: String,
    isSelected: Boolean,
    onSelect: (Side) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onSelect(side) },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) side.sideColor else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) onSideColor else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (isSelected) BorderStroke(1.dp, side.sideColor)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(52.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
        }
    }
}
