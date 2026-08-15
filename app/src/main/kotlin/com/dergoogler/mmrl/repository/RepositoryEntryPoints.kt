package com.dergoogler.mmrl.repository

import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.installer.UpdateRollbackStore
import com.dergoogler.mmrl.operation.ModuleMutationExecutor
import com.dergoogler.mmrl.operation.PrivilegedOperationCoordinator
import com.dergoogler.mmrl.operation.PrivilegedProcessExecutor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoints {
    fun localRepository(): LocalRepository

    fun modulesRepository(): ModulesRepository

    fun userPreferencesRepository(): UserPreferencesRepository

    fun operationHistoryRepository(): OperationHistoryRepository

    fun updateRollbackStore(): UpdateRollbackStore

    fun moduleMutationExecutor(): ModuleMutationExecutor

    fun privilegedOperationCoordinator(): PrivilegedOperationCoordinator

    fun privilegedProcessExecutor(): PrivilegedProcessExecutor
}
