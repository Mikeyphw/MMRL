package com.dergoogler.mmrl.viewmodel

internal object SuperUserInventoryPolicy {
    fun mergeVisibleWithPrivileged(visible: List<String>, privileged: List<String>): List<String> =
        (visible + privileged).distinct()
}
