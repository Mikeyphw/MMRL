package com.dergoogler.mmrl.ui.screens.moduleView.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.installer.ArchiveInspection
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.model.state.Permissions
import com.dergoogler.mmrl.ui.component.BottomSheet
import java.io.File

enum class InstallReviewPhase {
    REVIEW,
    DOWNLOADING,
    VERIFYING,
    INSPECTING,
    READY,
    BLOCKED,
    FAILED,
}

data class InstallReviewState(
    val phase: InstallReviewPhase = InstallReviewPhase.REVIEW,
    val file: File? = null,
    val inspection: ArchiveInspection? = null,
    val error: String? = null,
    val operationId: String? = null,
)

@Composable
internal fun InstallReviewBottomSheet(
    module: OnlineModule?,
    moduleName: String,
    version: VersionItem,
    installedVersion: String?,
    repositoryName: String,
    compatible: Boolean,
    requiresReboot: Boolean,
    requiresCount: Int,
    progress: Float,
    state: InstallReviewState,
    onClose: () -> Unit,
    onDownloadAndInspect: () -> Unit,
    onInstall: () -> Unit,
    onInstallWithDependencies: () -> Unit,
) = BottomSheet(onDismissRequest = onClose) {
    Text(
        text = stringResource(if (installedVersion == null) R.string.module_install else R.string.module_update),
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = moduleName,
        modifier = Modifier.padding(horizontal = 18.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

    ReviewStepper(state.phase)

    Column(
        modifier =
            Modifier
                .padding(horizontal = 18.dp)
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
    ) {
        when (state.phase) {
            InstallReviewPhase.REVIEW ->
                ReviewContent(
                    module = module,
                    version = version,
                    installedVersion = installedVersion,
                    repositoryName = repositoryName,
                    compatible = compatible,
                    requiresReboot = requiresReboot,
                    requiresCount = requiresCount,
                )
            InstallReviewPhase.DOWNLOADING -> ProgressContent(stringResource(R.string.install_review_downloading), progress)
            InstallReviewPhase.VERIFYING -> ProgressContent(stringResource(R.string.install_review_verifying), null)
            InstallReviewPhase.INSPECTING -> ProgressContent(stringResource(R.string.install_review_inspecting), null)
            InstallReviewPhase.READY, InstallReviewPhase.BLOCKED -> InspectionContent(checkNotNull(state.inspection))
            InstallReviewPhase.FAILED -> ErrorContent(state.error ?: stringResource(R.string.unknown_error))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
        when (state.phase) {
            InstallReviewPhase.REVIEW, InstallReviewPhase.FAILED -> {
                Button(onClick = onDownloadAndInspect) { Text(stringResource(R.string.install_review_continue)) }
            }
            InstallReviewPhase.READY -> {
                if (requiresCount > 0) {
                    TextButton(onClick = onInstallWithDependencies) {
                        Text(stringResource(R.string.view_module_install_confirm_confirm_deps))
                    }
                }
                Button(onClick = onInstall, enabled = compatible) {
                    Text(
                        stringResource(
                            if (installedVersion == null) R.string.module_install else R.string.module_update,
                        ),
                    )
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ReviewStepper(phase: InstallReviewPhase) {
    val labels = listOf(
        InstallReviewPhase.REVIEW to stringResource(R.string.install_review_review),
        InstallReviewPhase.DOWNLOADING to stringResource(R.string.install_review_download),
        InstallReviewPhase.VERIFYING to stringResource(R.string.install_review_verify),
        InstallReviewPhase.INSPECTING to stringResource(R.string.install_review_inspect),
        InstallReviewPhase.READY to stringResource(R.string.install_review_ready),
    )
    val active = when (phase) {
        InstallReviewPhase.BLOCKED, InstallReviewPhase.FAILED -> 4
        else -> labels.indexOfFirst { it.first == phase }.coerceAtLeast(0)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        labels.forEachIndexed { index, (_, label) ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = CircleShape,
                    color = if (index <= active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index <= active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(label, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReviewContent(
    module: OnlineModule?,
    version: VersionItem,
    installedVersion: String?,
    repositoryName: String,
    compatible: Boolean,
    requiresReboot: Boolean,
    requiresCount: Int,
) {
    val permissions = module?.permissions.orEmpty()
    val hasWebUi =
        module?.features?.webroot == true ||
            Permissions.KERNELSU_WEBUI in permissions ||
            Permissions.MMRL_WEBUI in permissions ||
            Permissions.MMRL_WEBUI_CONFIG in permissions
    ReviewLine(
        stringResource(R.string.module_details_installed_version),
        installedVersion ?: stringResource(R.string.module_details_not_installed),
    )
    ReviewLine(stringResource(R.string.module_details_available_version), version.versionDisplay)
    ReviewLine(stringResource(R.string.module_details_source_repository), repositoryName)
    ReviewLine(
        stringResource(R.string.module_details_compatibility),
        stringResource(if (compatible) R.string.repo_compatible else R.string.repo_incompatible),
    )
    ReviewLine(
        stringResource(R.string.modules_reboot_required),
        stringResource(if (requiresReboot) R.string.yes else R.string.no),
    )
    ReviewLine(
        stringResource(R.string.module_details_verification),
        stringResource(if (module?.isVerified == true) R.string.repo_verified else R.string.module_details_unverified),
    )
    ReviewLine(
        stringResource(R.string.module_details_webui),
        stringResource(if (hasWebUi) R.string.yes else R.string.no),
    )
    if (installedVersion != null) {
        ReviewLine(
            stringResource(R.string.install_review_rollback),
            stringResource(R.string.install_review_rollback_previous_version, installedVersion),
        )
    }
    if (requiresCount > 0) ReviewLine(stringResource(R.string.view_module_install_confirm_requires), requiresCount.toString())
    if (module?.track?.antifeatures.orEmpty().isNotEmpty()) {
        WarningBox(stringResource(R.string.module_details_antifeatures, module?.track?.antifeatures.orEmpty().size), MaterialTheme.colorScheme.error)
    }
    Text(
        text = version.changelog.ifBlank { stringResource(R.string.updates_no_changelog) },
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun InspectionContent(inspection: ArchiveInspection) {
    ReviewLine("SHA-256", inspection.sha256.take(16) + "…")
    ReviewLine(stringResource(R.string.module_details_files), inspection.entryCount.toString())
    ReviewLine(stringResource(R.string.install_review_scripts), inspection.scripts.size.toString())
    ReviewLine(stringResource(R.string.install_review_binaries), inspection.nativeBinaries.size.toString())
    ReviewLine(stringResource(R.string.install_review_apks), inspection.apks.size.toString())
    ReviewLine("SELinux", inspection.sePolicyFiles.size.toString())
    ReviewLine(stringResource(R.string.install_review_properties), inspection.propertyFiles.size.toString())
    InspectionFileGroup(stringResource(R.string.install_review_scripts), inspection.scripts)
    InspectionFileGroup(stringResource(R.string.install_review_binaries), inspection.nativeBinaries)
    InspectionFileGroup(stringResource(R.string.install_review_apks), inspection.apks)
    InspectionFileGroup("SELinux", inspection.sePolicyFiles)
    InspectionFileGroup(stringResource(R.string.install_review_properties), inspection.propertyFiles)
    inspection.warnings.forEach { WarningBox(it, MaterialTheme.colorScheme.tertiary) }
    inspection.blockedReasons.forEach { WarningBox(it, MaterialTheme.colorScheme.error) }
    if (inspection.canInstall) {
        WarningBox(stringResource(R.string.install_review_safe_to_continue), MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InspectionFileGroup(label: String, files: List<String>) {
    if (files.isEmpty()) return
    Text(
        text = label,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    files.take(5).forEach { file ->
        Text(
            text = file,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (files.size > 5) {
        Text(
            text = stringResource(R.string.install_review_more_files, files.size - 5),
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ProgressContent(label: String, progress: Float?) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress == null || progress <= 0f) CircularProgressIndicator() else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(label, modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.install_review_activity_note), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorContent(message: String) = WarningBox(message, MaterialTheme.colorScheme.error)

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(.42f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(.58f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WarningBox(text: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = color.copy(alpha = .1f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
    ) {
        Text(text, modifier = Modifier.padding(11.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
