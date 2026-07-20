package com.dergoogler.mmrl.ui.screens.home.items

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ash.model.AshProtectionStatus
import com.dergoogler.mmrl.ash.model.AshProtectionSummary

@Composable
internal fun AshProtectionCard(
    summary: AshProtectionSummary,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val statusColor =
        when (summary.status) {
            AshProtectionStatus.Stable -> MaterialTheme.colorScheme.primary
            AshProtectionStatus.Monitoring,
            AshProtectionStatus.Cached,
            -> MaterialTheme.colorScheme.tertiary
            AshProtectionStatus.RestorationTrial,
            AshProtectionStatus.Quarantined,
            AshProtectionStatus.UpdateRequired,
            AshProtectionStatus.RebootPending,
            -> MaterialTheme.colorScheme.error
            AshProtectionStatus.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    color = statusColor.copy(alpha = 0.14f),
                    contentColor = statusColor,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shield_bolt),
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        painter = painterResource(R.drawable.refresh),
                        contentDescription = "Refresh AshReXcue protection",
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ProtectionMetric(
                    text = stringResource(R.string.ash_metric_failed_boots, summary.failedBoots, summary.failureThreshold),
                    color = if (summary.failedBoots > 0) MaterialTheme.colorScheme.error else statusColor,
                )
                ProtectionMetric(
                    text = stringResource(R.string.ash_metric_quarantined, summary.quarantinedModules),
                    color = if (summary.quarantinedModules > 0) MaterialTheme.colorScheme.error else statusColor,
                )
                ProtectionMetric(
                    text = stringResource(R.string.ash_metric_protected, summary.protectedModules),
                    color = statusColor,
                )
                if (summary.readOnly) {
                    ProtectionMetric(text = stringResource(R.string.recovery_read_only_state), color = MaterialTheme.colorScheme.tertiary)
                }
            }

            if (summary.lastSuccessfulAt > 0) {
                Text(
                    text = stringResource(
                        R.string.ash_last_checked_relative,
                        DateUtils.getRelativeTimeSpanString(
                            summary.lastSuccessfulAt.asMilliseconds(),
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProtectionMetric(
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(7.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun Long.asMilliseconds(): Long = if (this in 1..9_999_999_999L) this * 1_000L else this
