package com.dergoogler.mmrl.repository

import android.content.Context
import com.dergoogler.mmrl.database.entity.Repo
import com.dergoogler.mmrl.github.GitHubTokenStore
import com.dergoogler.mmrl.network.runRequest
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.LocalModule
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.stub.IMMRLApiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModulesRepository
    @Inject
    constructor(
        private val localRepository: LocalRepository,
        @ApplicationContext context: Context,
    ) {
        private val githubTokenStore = GitHubTokenStore(context)

        suspend fun getLocalAll() =
            withContext(Dispatchers.IO) {
                val modules = PlatformManager.get<List<LocalModule>?>(null) { moduleManager.modules }
                    ?: return@withContext
                localRepository.replaceLocalGeneration(modules)
            }

        suspend fun getLocal(id: ModId) =
            withContext(Dispatchers.IO) {
                val module = PlatformManager.get<LocalModule?>(null) { moduleManager.getModuleById(id) }
                    ?: return@withContext
                localRepository.insertLocal(module)
            }

        suspend fun getRepoAll(onlyEnable: Boolean = true) =
            localRepository
                .getRepoAll()
                .filter {
                    if (onlyEnable) it.enable else true
                }.map {
                    getRepo(it)
                }

        suspend fun getRepo(repo: Repo): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val modulesJson = RepositorySourceLoader.load(repo.url, githubTokenStore.getToken()).getOrThrow()
                    localRepository.replaceRepositoryGeneration(repo, modulesJson)
                }.onFailure {
                    Timber.e(it, "getRepo: ${repo.url}")
                }
            }

        suspend fun getBlacklist() =
            withContext(Dispatchers.IO) {
                runRequest {
                    val api = IMMRLApiManager.build()
                    return@runRequest api.blacklist.execute()
                }.onSuccess { blacklist ->
                    blacklist.map {
                        localRepository.deleteBlacklistById(it.id)
                        localRepository.insertBlacklist(it)
                    }
                }.onFailure {
                    Timber.e(it, "getBlacklist: Failed to get blacklist")
                }
            }
    }
