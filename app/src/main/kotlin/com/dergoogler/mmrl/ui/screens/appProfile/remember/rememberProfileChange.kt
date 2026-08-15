package com.dergoogler.mmrl.ui.screens.appProfile.remember

import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.platform.SePolicy
import com.dergoogler.mmrl.platform.ksu.KsuNative
import com.dergoogler.mmrl.platform.ksu.Profile
import com.dergoogler.mmrl.platform.ksu.ProfileMutationTransaction
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.providable.LocalSuperUserViewModel
import com.dergoogler.mmrl.viewmodel.SuperUserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberProfileChange(info: SuperUserViewModel.AppInfo): Pair<Profile, (Profile) -> Unit> {
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val viewModel = LocalSuperUserViewModel.current

    val initialProfile =
        remember(info.packageName, info.uid, info.profile) {
            info.profile ?: Profile(info.packageName, currentUid = info.uid)
        }

    var profile by rememberSaveable(info.packageName, info.uid) {
        mutableStateOf(initialProfile)
    }

    // Cache string resources to avoid recomposition
    val errorMessages =
        remember {
            ErrorMessages(
                failToUpdateAppProfile = R.string.failed_to_update_app_profile,
                failToUpdateSepolicy = R.string.failed_to_update_sepolicy,
                suNotAllowed = R.string.su_not_allowed,
            )
        }

    val updateProfile: (Profile) -> Unit =
        remember(info, profile, snackBarHost, scope, viewModel) {
            { newProfile ->
                scope.launch {
                    try {
                        val result =
                            updateProfileSafely(
                                previousProfile = profile,
                                profile = newProfile,
                                info = info,
                                errorMessages,
                                snackBarHost,
                            )
                        if (result.isSuccess) {
                            profile = newProfile
                            viewModel.updateAppProfile(info.packageName, newProfile)
                        }
                    } catch (e: Exception) {
                        Log.e("ProfileChange", "Error updating profile", e)
                        snackBarHost.showSnackbar("An unexpected error occurred")
                    }
                }
            }
        }

    return profile to updateProfile
}

private data class ErrorMessages(
    val failToUpdateAppProfile: Int,
    val failToUpdateSepolicy: Int,
    val suNotAllowed: Int,
)

private sealed class UpdateResult {
    object Success : UpdateResult()

    data class Error(
        val messageRes: Int,
        val args: List<Any>,
    ) : UpdateResult()

    val isSuccess: Boolean get() = this is Success
}

private suspend fun updateProfileSafely(
    previousProfile: Profile,
    profile: Profile,
    info: SuperUserViewModel.AppInfo,
    errorMessages: ErrorMessages,
    snackBarHost: SnackbarHostState,
): UpdateResult {
    if (profile.allowSu && isSystemUidForbidden(info.uid)) {
        snackBarHost.showSnackbar("SU not allowed for system UID ${info.uid}")
        return UpdateResult.Error(errorMessages.suNotAllowed, listOf(info.label))
    }

    val transaction = withContext(Dispatchers.IO) {
        ProfileMutationTransaction.execute(
            previous = previousProfile,
            target = profile,
            backend = object : ProfileMutationTransaction.Backend {
                override fun setProfile(profile: Profile): Boolean = KsuNative.setAppProfile(profile)
                override fun setPolicy(packageName: String, rules: String): Boolean = SePolicy.setSePolicy(packageName, rules)
                override fun clearPolicy(packageName: String): Boolean = SePolicy.clearSePolicy(packageName)
            },
        )
    }
    if (!transaction.success) {
        val detail = when {
            transaction.reconciliationRequired -> " Live SELinux state may already have changed; reboot/reconcile before retrying."
            transaction.rolledBack -> " Previous state was restored."
            else -> " Reconcile current root state before retrying."
        }
        snackBarHost.showSnackbar("Failed to update root profile.${detail}")
        return UpdateResult.Error(errorMessages.failToUpdateAppProfile, listOf(info.uid))
    }

    if (transaction.reconciliationRequired) {
        snackBarHost.showSnackbar("Profile updated. Reboot/reconcile to retire previous live SELinux rules.")
    }
    return UpdateResult.Success
}

private fun isSystemUidForbidden(uid: Int): Boolean {
    // sync with allowlist.c - forbid_system_uid
    return uid < 2000 && uid != 1000
}


val LocalProfileChange =
    staticCompositionLocalOf<Pair<Profile, (Profile) -> Unit>> {
        error("CompositionLocal LocalProfileChange not present")
    }
