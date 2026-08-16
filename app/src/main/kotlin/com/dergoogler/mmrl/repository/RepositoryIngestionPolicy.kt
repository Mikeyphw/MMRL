package com.dergoogler.mmrl.repository

import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.online.ModulesJson
import com.dergoogler.mmrl.network.NetworkPolicy

internal object RepositoryIngestionPolicy {
    fun validateModulesJson(value: ModulesJson): ModulesJson {
        require(value.modules.size <= NetworkPolicy.MAX_REPOSITORY_MODULES) {
            "Repository contains ${value.modules.size} modules; maximum is ${NetworkPolicy.MAX_REPOSITORY_MODULES}"
        }
        require(value.modules.none { it.id.isBlank() || ModuleIdentity.normalize(it.id).isBlank() }) {
            "Repository contains a module with a blank or invalid id"
        }
        val duplicate =
            value.modules
                .groupBy { ModuleIdentity.canonical(it.id) }
                .entries
                .firstOrNull { it.value.size > 1 }
        require(duplicate == null) {
            "Repository contains duplicate module id aliases: ${duplicate?.key}"
        }
        return value
    }

    fun validateKernelSuCatalog(entries: List<RepositorySourceLoader.KernelSuCatalogEntry>): List<RepositorySourceLoader.KernelSuCatalogEntry> {
        require(entries.size <= NetworkPolicy.MAX_KERNELSU_CATALOG_ENTRIES) {
            "KernelSU catalog contains ${entries.size} entries; maximum is ${NetworkPolicy.MAX_KERNELSU_CATALOG_ENTRIES}"
        }
        return entries
    }
}
