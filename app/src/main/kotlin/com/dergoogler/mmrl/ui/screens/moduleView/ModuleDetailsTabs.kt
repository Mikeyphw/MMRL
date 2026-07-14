package com.dergoogler.mmrl.ui.screens.moduleView

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.model.online.ModuleFeatures
import com.dergoogler.mmrl.model.state.Permissions
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.isEmpty
import com.dergoogler.mmrl.ui.providable.LocalModule
import com.dergoogler.mmrl.ui.providable.LocalOnlineModule
import com.dergoogler.mmrl.ui.providable.LocalRepo
import com.dergoogler.mmrl.ui.providable.LocalVersionItem
import com.dergoogler.mmrl.ui.screens.moduleView.sections.AboutModule
import com.dergoogler.mmrl.ui.screens.moduleView.sections.Alerts
import com.dergoogler.mmrl.ui.screens.moduleView.sections.Information
import com.dergoogler.mmrl.ui.screens.moduleView.sections.Information0
import com.dergoogler.mmrl.ui.screens.moduleView.sections.Screenshots
import com.dergoogler.mmrl.ui.theme.LocalSemanticColors

private enum class DetailsPage { OVERVIEW, CHANGES, FILES, DETAILS }

@Composable
internal fun ModuleDecisionSummary() {
    val module = LocalOnlineModule.current
    val local = LocalModule.current
    val version = LocalVersionItem.current
    val repo = LocalRepo.current
    val rootVersion = PlatformManager.get(0) { with(moduleManager) { versionCode } }
    val compatible = remember(module, rootVersion) {
        module.manager(PlatformManager.platform).isCompatible(rootVersion) &&
            (module.minApi == null || Build.VERSION.SDK_INT >= module.minApi) &&
            (module.maxApi == null || Build.VERSION.SDK_INT <= module.maxApi)
    }
    val permissions = module.permissions.orEmpty()
    val hasWebUi = module.features?.webroot == true ||
        Permissions.KERNELSU_WEBUI in permissions ||
        Permissions.MMRL_WEBUI in permissions ||
        Permissions.MMRL_WEBUI_CONFIG in permissions
    val antiFeatures = module.track.antifeatures.orEmpty()
    val semantic = LocalSemanticColors.current

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        DecisionTag(
            text = stringResource(if (compatible) R.string.repo_compatible else R.string.repo_incompatible),
            color = if (compatible) semantic.success else semantic.incompatible,
        )
        if (!local.isEmpty) {
            DecisionTag("${local.version} → ${version.version}", MaterialTheme.colorScheme.secondary)
        } else {
            DecisionTag(version.versionDisplay, MaterialTheme.colorScheme.secondary)
        }
        DecisionTag(repo.name, MaterialTheme.colorScheme.onSurfaceVariant)
        DecisionTag(stringResource(R.string.modules_reboot_required), semantic.rebootRequired)
        if (hasWebUi) DecisionTag("WebUI", semantic.info)
        if (module.isVerified) DecisionTag(stringResource(R.string.repo_verified), semantic.verified)
        if (antiFeatures.isNotEmpty()) {
            DecisionTag(
                stringResource(R.string.module_details_antifeatures, antiFeatures.size),
                semantic.warning,
            )
        }
    }
}

@Composable
internal fun ModuleDetailsTabs() {
    val pages = DetailsPage.entries
    val labels = listOf(
        stringResource(R.string.module_details_overview),
        stringResource(R.string.module_details_changes),
        stringResource(R.string.module_details_files),
        stringResource(R.string.module_details_details),
    )
    var selected by remember { mutableIntStateOf(0) }
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    if (wide) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(modifier = Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.forEachIndexed { index, label ->
                    Surface(
                        onClick = { selected = index },
                        color = if (selected == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (selected == index) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                DetailsPageContent(pages[selected])
            }
        }
    } else {
        TabRow(selectedTabIndex = selected, divider = {}) {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(label, maxLines = 1) },
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            DetailsPageContent(pages[selected])
        }
    }
}

@Composable
private fun DetailsPageContent(page: DetailsPage) {
    when (page) {
        DetailsPage.OVERVIEW -> {
            Alerts()
            AboutModule()
            Screenshots()
        }
        DetailsPage.CHANGES -> ChangesPage()
        DetailsPage.FILES -> FilesPage()
        DetailsPage.DETAILS -> {
            Information0()
            Information()
        }
    }
}

@Composable
private fun ChangesPage() {
    val module = LocalOnlineModule.current
    val local = LocalModule.current
    val version = LocalVersionItem.current
    SectionBlock(stringResource(R.string.module_details_version_change)) {
        DetailValue(stringResource(R.string.module_details_installed_version), if (!local.isEmpty) local.version else stringResource(R.string.module_details_not_installed))
        DetailValue(stringResource(R.string.module_details_available_version), version.versionDisplay)
    }
    SectionBlock(stringResource(R.string.module_details_changelog)) {
        Text(
            text = version.changelog.ifBlank { stringResource(R.string.updates_no_changelog) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val features = module.features
    if (features?.isNotEmpty() == true || module.permissions.orEmpty().isNotEmpty()) {
        SectionBlock(stringResource(R.string.module_details_declared_changes)) {
            FeatureRows(features)
        }
    }
}

@Composable
private fun FilesPage() {
    val module = LocalOnlineModule.current
    val features = module.features
    val permissions = module.permissions.orEmpty()
    Text(
        text = stringResource(R.string.module_details_files_explanation),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionBlock(stringResource(R.string.module_details_declared_files)) {
        FeatureRows(features)
        if (permissions.isEmpty() && features?.isNotEmpty() != true) {
            Text(stringResource(R.string.module_details_no_declared_files), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        permissions.forEach { permission -> DetailValue(stringResource(R.string.module_details_permission), permission) }
    }
}

@Composable
private fun FeatureRows(features: ModuleFeatures?) {
    if (features == null) return
    if (features.service == true) DetailValue("service.sh", stringResource(R.string.updates_boot_scripts))
    if (features.postFsData == true) DetailValue("post-fs-data.sh", stringResource(R.string.updates_boot_scripts))
    if (features.postMount == true) DetailValue("post-mount.sh", stringResource(R.string.updates_boot_scripts))
    if (features.bootCompleted == true) DetailValue("boot-completed.sh", stringResource(R.string.updates_boot_scripts))
    if (features.action == true) DetailValue("action.sh", stringResource(R.string.activity_kind_action))
    if (features.apks == true) DetailValue("APK", stringResource(R.string.module_details_bundled_apks))
    if (features.sepolicy == true) DetailValue("sepolicy.rule", stringResource(R.string.updates_sensitive_changes))
    if (features.resetprop == true) DetailValue("system.prop", stringResource(R.string.updates_sensitive_changes))
    if (features.webroot == true) DetailValue("webroot", "WebUI")
    if (features.zygisk == true) DetailValue("zygisk", stringResource(R.string.module_details_native_code))
}

@Composable
private fun SectionBlock(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(.4f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(.6f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DecisionTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = .12f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
