package com.dergoogler.mmrl.datastore.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskerApprovalPolicy {
    ALWAYS_ASK,
    DEVICE_UNLOCKED,
    MODULE_ALLOWLIST,
    NEVER,
}
