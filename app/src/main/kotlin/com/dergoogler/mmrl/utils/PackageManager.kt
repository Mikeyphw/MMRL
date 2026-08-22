package com.dergoogler.mmrl.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

private fun PackageManager.getInstalledPackagesCompat(): List<PackageInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getInstalledPackages(
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        getInstalledPackages(0)
    }
}


val Context.packages: List<PackageInfo>
    get() = packageManager.getInstalledPackagesCompat()