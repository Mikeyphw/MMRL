package com.dergoogler.mmrl.app

import android.content.Context
import com.dergoogler.mmrl.ash.automation.AshAutomationScheduler
import com.dergoogler.mmrl.ash.data.AshMutationJournal
import com.dergoogler.mmrl.datastore.model.UserPreferences
import com.dergoogler.mmrl.network.NetworkUtils
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import com.dergoogler.mmrl.service.ModuleService
import com.dergoogler.mmrl.service.ProviderService
import com.dergoogler.mmrl.service.RepositoryService

object AppStartupCoordinator {
    suspend fun restore(
        context: Context,
        preferences: UserPreferences,
        operationHistoryRepository: OperationHistoryRepository,
    ) {
        NetworkUtils.setEnableDoh(preferences.useDoh)
        operationHistoryRepository.enforceRetention()
        operationHistoryRepository.recoverAfterProcessRestart()
        operationHistoryRepository.recoverStaleOperations()
        AshMutationJournal(context).interrupted().forEach { entry ->
            operationHistoryRepository.outcomeUnknown(
                id = entry.operationId,
                summary = "AshReXcue mutation was interrupted in ${entry.stage.name}; reconcile backend state before retrying",
            )
        }
        if (preferences.providerServiceEnabled) {
            runCatching { ProviderService.start(context, preferences.workingMode) }
        } else {
            runCatching { ProviderService.stop(context) }
        }
        if (preferences.repositoryServiceEnabled) {
            runCatching { RepositoryService.start(context, preferences.autoUpdateReposInterval) }
        } else {
            runCatching { RepositoryService.stop(context) }
        }
        if (preferences.moduleServiceEnabled) {
            runCatching { ModuleService.start(context, preferences.checkModuleUpdatesInterval) }
        } else {
            runCatching { ModuleService.stop(context) }
        }
        AshAutomationScheduler.synchronize(
            context = context,
            enabled = preferences.ashHealthChecksEnabled,
            intervalHours = preferences.ashHealthCheckIntervalHours,
        )
    }
}
