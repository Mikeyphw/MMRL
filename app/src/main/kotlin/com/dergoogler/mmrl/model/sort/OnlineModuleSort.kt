package com.dergoogler.mmrl.model.sort

import com.dergoogler.mmrl.datastore.model.Option
import com.dergoogler.mmrl.datastore.model.RepositoryMenu
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.state.OnlineState
import java.util.Locale

typealias OnlineModuleEntry = Pair<OnlineState, OnlineModule>

/**
 * Applies the repository's complete ordering policy to an immutable copy of the source list.
 *
 * Pinning is intentionally part of the comparator instead of being applied as follow-up sorts.
 * That keeps ordering deterministic and prevents future changes from accidentally sorting only a
 * visible/page-sized subset.
 */
fun Iterable<OnlineModuleEntry>.sortedForRepository(menu: RepositoryMenu): List<OnlineModuleEntry> =
    toList().sortedWith(repositoryComparator(menu))

fun repositoryComparator(menu: RepositoryMenu): Comparator<OnlineModuleEntry> =
    Comparator { left, right ->
        comparePinned(left, right, menu)
            .takeIf { it != 0 }
            ?: comparePrimary(left, right, menu.option, menu.descending)
                .takeIf { it != 0 }
            ?: compareText(left.second.name, right.second.name)
                .takeIf { it != 0 }
            ?: compareText(left.second.id, right.second.id)
    }

private fun comparePinned(
    left: OnlineModuleEntry,
    right: OnlineModuleEntry,
    menu: RepositoryMenu,
): Int {
    if (menu.pinUpdatable) {
        compareValues(right.first.updatable, left.first.updatable).takeIf { it != 0 }?.let {
            return it
        }
    }

    if (menu.pinInstalled) {
        compareValues(right.first.installed, left.first.installed).takeIf { it != 0 }?.let {
            return it
        }
    }

    return 0
}

private fun comparePrimary(
    left: OnlineModuleEntry,
    right: OnlineModuleEntry,
    option: Option,
    descending: Boolean,
): Int =
    when (option) {
        Option.Name -> compareOrdered(left.second.name, right.second.name, descending, ::compareText)
        Option.UpdatedTime ->
            compareNullable(
                left.first.lastUpdated.takeIf { it > 0f },
                right.first.lastUpdated.takeIf { it > 0f },
                descending,
                { leftValue, rightValue -> leftValue.compareTo(rightValue) },
            )
        Option.Size ->
            compareNullable(
                left.second.effectiveDownloadSize,
                right.second.effectiveDownloadSize,
                descending,
                { leftValue, rightValue -> leftValue.compareTo(rightValue) },
            )
    }

private val OnlineModule.effectiveDownloadSize: Int?
    get() = size ?: versions.firstOrNull()?.size

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

/** Missing metadata always stays at the end, independent of direction. */
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
