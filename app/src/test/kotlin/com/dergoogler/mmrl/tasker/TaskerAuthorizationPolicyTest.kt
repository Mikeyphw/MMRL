package com.dergoogler.mmrl.tasker

import com.dergoogler.mmrl.datastore.model.TaskerApprovalPolicy
import com.dergoogler.mmrl.datastore.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskerAuthorizationPolicyTest {
    @Test
    fun `disabled integration denies privileged actions`() {
        assertEquals(
            TaskerAuthorizationDecision.DENY,
            TaskerAuthorizationPolicy.decide(
                preferences = UserPreferences(taskerIntegrationEnabled = false, taskerAllowStateChanges = true),
                capability = TaskerCapability.STATE_CHANGE,
                moduleId = "example",
                deviceUnlocked = true,
            ),
        )
    }

    @Test
    fun `unlocked policy executes only while device is unlocked`() {
        val preferences = UserPreferences(
            taskerIntegrationEnabled = true,
            taskerAllowStateChanges = true,
            taskerApprovalPolicy = TaskerApprovalPolicy.DEVICE_UNLOCKED,
        )
        assertEquals(
            TaskerAuthorizationDecision.EXECUTE,
            TaskerAuthorizationPolicy.decide(preferences, TaskerCapability.STATE_CHANGE, "example", true),
        )
        assertEquals(
            TaskerAuthorizationDecision.REQUIRE_APPROVAL,
            TaskerAuthorizationPolicy.decide(preferences, TaskerCapability.STATE_CHANGE, "example", false),
        )
    }

    @Test
    fun `allowlist uses normalized module identity`() {
        val preferences = UserPreferences(
            taskerIntegrationEnabled = true,
            taskerAllowReviewedInstalls = true,
            taskerApprovalPolicy = TaskerApprovalPolicy.MODULE_ALLOWLIST,
            taskerAllowedModules = setOf("  Example.Module  "),
        )
        assertEquals(
            TaskerAuthorizationDecision.EXECUTE,
            TaskerAuthorizationPolicy.decide(preferences, TaskerCapability.REVIEWED_INSTALL, "example.module", false),
        )
    }

    @Test
    fun `capability toggle still denies allowlisted module`() {
        val preferences = UserPreferences(
            taskerIntegrationEnabled = true,
            taskerAllowRemovals = false,
            taskerApprovalPolicy = TaskerApprovalPolicy.MODULE_ALLOWLIST,
            taskerAllowedModules = setOf("example"),
        )
        assertEquals(
            TaskerAuthorizationDecision.DENY,
            TaskerAuthorizationPolicy.decide(preferences, TaskerCapability.REMOVAL, "example", true),
        )
    }
    @Test
    fun `non routine reviewed install always requires approval`() {
        assertEquals(
            TaskerAuthorizationDecision.REQUIRE_APPROVAL,
            TaskerAuthorizationPolicy.reviewedInstallDecision(
                policyDecision = TaskerAuthorizationDecision.EXECUTE,
                routine = false,
            ),
        )
        assertEquals(
            TaskerAuthorizationDecision.EXECUTE,
            TaskerAuthorizationPolicy.reviewedInstallDecision(
                policyDecision = TaskerAuthorizationDecision.EXECUTE,
                routine = true,
            ),
        )
    }

}
