package com.harding.feeds.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Which kinds of event a surface shows.
 *
 * Shared by the day timeline and the charts screen. Both answer the same question - feeds, naps,
 * or both - and a parent should not have to learn two controls for it.
 */
enum class EventFilter { FEEDS, BOTH, NAPS }

/**
 * The filter control: three words in a pill, the selected one filled.
 *
 * It carries no position of its own. The timeline floats it over the bottom of the history
 * sheet and pads it for the window insets; the charts screen sets it inline. Baking either into
 * the component would make it wrong on the other surface.
 */
@Composable
fun EventFilterPill(
    selected: EventFilter,
    onSelect: (EventFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        // Lifts it off whatever passes underneath; without this the labels collide with the
        // text they are floating over.
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            EventFilter.entries.forEach { filter ->
                val isSelected = filter == selected
                Surface(
                    onClick = { onSelect(filter) },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        filter.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}
