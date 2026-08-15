package com.dergoogler.mmrl.installer

/** Truthful batch terminal summary after dependency-first sequential installation. */
object BatchInstallOutcomePolicy {
    enum class Kind { SUCCEEDED, FAILED, PARTIAL_SUCCESS }

    data class Outcome(val kind: Kind, val summary: String, val requiresReboot: Boolean)

    fun classify(totalRequested: Int, installed: Int, hadFailure: Boolean): Outcome {
        require(totalRequested >= 0 && installed >= 0 && installed <= totalRequested)
        return when {
            !hadFailure && installed == totalRequested -> Outcome(
                Kind.SUCCEEDED,
                "Installed all $installed module artifact(s)",
                installed > 0,
            )
            installed > 0 -> Outcome(
                Kind.PARTIAL_SUCCESS,
                "Installed $installed of $totalRequested module artifact(s); batch stopped after a failure",
                true,
            )
            else -> Outcome(
                Kind.FAILED,
                "No module artifacts were installed",
                false,
            )
        }
    }
}
