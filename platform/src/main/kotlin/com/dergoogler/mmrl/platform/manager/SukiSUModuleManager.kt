package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.Platform

open class SukiSUModuleManager : KernelSUModuleManager(Platform.SukiSU) {
    override fun getManagerName(): String = "SukiSU"


    override fun getActionEnvironment(): List<String> =
        listOf(
            "export ASH_STANDALONE=1",
            "export KSU=true",
            "export KSU_SUKISU=true",
            "export KSU_VER=$version",
            "export KSU_VER_CODE=$versionCode",
        )
}
