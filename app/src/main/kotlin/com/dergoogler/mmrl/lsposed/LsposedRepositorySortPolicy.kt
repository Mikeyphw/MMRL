package com.dergoogler.mmrl.lsposed

import com.dergoogler.mmrl.datastore.model.Option
import com.dergoogler.mmrl.datastore.model.RepositoryMenu
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/** Applies the same persisted repository ordering controls to LSPosed repository modules. */
object LsposedRepositorySortPolicy {
    fun sort(
        modules: Iterable<LsposedRepoModule>,
        installed: Iterable<LsposedInstalledModule>,
        menu: RepositoryMenu,
    ): List<LsposedRepoModule> {
        val installedByPackage = installed.associateBy { LsposedIdentity.normalize(it.packageName) }
        return modules.toList().sortedWith { left, right ->
            comparePinned(left, right, installedByPackage, menu)
                .takeIf { it != 0 }
                ?: comparePrimary(left, right, menu.option, menu.descending)
                    .takeIf { it != 0 }
                ?: compareText(left.displayName, right.displayName)
                    .takeIf { it != 0 }
                ?: compareText(left.packageName, right.packageName)
        }
    }

    private fun comparePinned(
        left: LsposedRepoModule,
        right: LsposedRepoModule,
        installedByPackage: Map<String, LsposedInstalledModule>,
        menu: RepositoryMenu,
    ): Int {
        val leftInstalled = installedByPackage[LsposedIdentity.normalize(left.packageName)]
        val rightInstalled = installedByPackage[LsposedIdentity.normalize(right.packageName)]

        if (menu.pinUpdatable) {
            compareValues(rightInstalled?.hasUpdate == true, leftInstalled?.hasUpdate == true)
                .takeIf { it != 0 }
                ?.let { return it }
        }
        if (menu.pinInstalled) {
            compareValues(rightInstalled != null, leftInstalled != null)
                .takeIf { it != 0 }
                ?.let { return it }
        }
        return 0
    }

    private fun comparePrimary(
        left: LsposedRepoModule,
        right: LsposedRepoModule,
        option: Option,
        descending: Boolean,
    ): Int =
        when (option) {
            Option.Name -> compareOrdered(left.displayName, right.displayName, descending, ::compareText)
            Option.UpdatedTime -> compareNullable(
                timestamp(left.latestStableTime),
                timestamp(right.latestStableTime),
                descending,
                { leftValue, rightValue -> leftValue.compareTo(rightValue) },
            )
            Option.Size -> compareNullable(
                apkSize(left),
                apkSize(right),
                descending,
                { leftValue, rightValue -> leftValue.compareTo(rightValue) },
            )
        }

    private fun apkSize(module: LsposedRepoModule): Int? =
        LsposedModulePolicy.bestInstallAsset(module)
            ?.second
            ?.size
            ?.takeIf { it > 0 }

    private fun timestamp(value: String?): Long? {
        val raw = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .getOrNull()
    }

    private fun compareText(left: String, right: String): Int =
        left.lowercase(Locale.ROOT).compareTo(right.lowercase(Locale.ROOT))

    private fun <T> compareOrdered(
        left: T,
        right: T,
        descending: Boolean,
        comparator: (T, T) -> Int,
    ): Int {
        val result = comparator(left, right)
        return if (descending) -result else result
    }

    /** Missing LSPosed metadata remains at the end, independent of direction. */
    private fun <T> compareNullable(
        left: T?,
        right: T?,
        descending: Boolean,
        comparator: (T, T) -> Int,
    ): Int =
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> compareOrdered(left, right, descending, comparator)
        }
}
