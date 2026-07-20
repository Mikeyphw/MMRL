package com.dergoogler.mmrl.ash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.ui.component.ScrollableDialogColumn
import com.dergoogler.mmrl.ui.component.StatusPill

@Composable
internal fun StatusPill(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    StatusPill(text = text, color = color, modifier = modifier, stateDescription = text)
}

@Composable
internal fun ScrollableRecoveryDialogContent(
    content: @Composable ColumnScope.() -> Unit,
) {
    ScrollableDialogColumn(content = content)
}

@Composable
internal fun RecoveryActionLabel(
    running: Boolean,
    label: String,
) {
    Row(
        modifier = Modifier.semantics {
            if (running) liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(label)
    }
}
