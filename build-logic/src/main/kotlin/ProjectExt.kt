@file:Suppress("unused", "UnusedReceiverParameter")

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import java.io.File
import java.util.Properties

// #### CONFIG START ####

const val COMPILE_SDK = 36
const val TARGET_SDK = 36
const val BUILD_TOOLS_VERSION = "36.0.0"
const val NDK_VERSION = "28.2.13676358"
const val MIN_SDK = 26

// ####  CONFIG END  ####

/**
 * Git metadata is useful for official builds, but release configuration must also work from
 * clean source archives where .git is intentionally absent. Explicit Gradle properties win;
 * otherwise git is queried best-effort and finally a deterministic archive-safe fallback is used.
 */
val Project.commitId: String
    get() = providers.gradleProperty("mmrl.commitId")
        .orElse(providers.environmentVariable("GITHUB_SHA").map { it.take(12) })
        .orElse(providers.provider { execOrNull("git", "rev-parse", "--short", "HEAD") ?: "archive" })
        .get()

val Project.commitCount: Int
    get() = providers.gradleProperty("mmrl.commitCount")
        .map { it.toIntOrNull() ?: 0 }
        .orElse(providers.provider { execOrNull("git", "rev-list", "--count", "HEAD")?.toIntOrNull() ?: 0 })
        .get()

fun Project.resolveVersionCode(base: Int): Int =
    providers.gradleProperty("mmrl.versionCode")
        .map { value ->
            requireNotNull(value.toIntOrNull()) { "mmrl.versionCode must be an integer" }
        }
        .orElse(providers.provider { base + commitCount })
        .get()

fun Project.execOrNull(vararg command: String): String? =
    runCatching {
        providers.exec {
            commandLine(*command)
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().takeIf(String::isNotBlank)
    }.getOrNull()

fun Project.exec(command: String): String =
    execOrNull(*command.split(" ").toTypedArray())
        ?: throw GradleException("Command failed or produced no output: $command")

data class ReleaseSigningProperties(
    val keyStore: File,
    val keyStorePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun Project.releaseSigningProperties(): ReleaseSigningProperties? {
    val properties = signingProperties(rootDir)
    if (properties.isEmpty) return null

    val required = listOf("keyStore", "keyStorePassword", "keyAlias", "keyPassword")
    val missing = required.filter { properties.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        throw GradleException("Incomplete signing.properties; missing ${missing.joinToString()} for release signing")
    }

    val configuredStore = File(properties.getProperty("keyStore"))
    val resolvedStore = if (configuredStore.isAbsolute) configuredStore else rootDir.resolve(configuredStore.path)
    if (!resolvedStore.isFile) {
        throw GradleException("Release signing keyStore does not exist: ${resolvedStore.absolutePath}")
    }

    properties.forEach { key, value -> extra[key as String] = value }
    return ReleaseSigningProperties(
        keyStore = resolvedStore,
        keyStorePassword = properties.getProperty("keyStorePassword"),
        keyAlias = properties.getProperty("keyAlias"),
        keyPassword = properties.getProperty("keyPassword"),
    )
}

val Project.releaseKeyStore: File get() = releaseSigningProperties()?.keyStore
    ?: throw GradleException("Release signing.properties is required for release/playstore artifacts")
val Project.releaseKeyStorePassword: String get() = releaseSigningProperties()?.keyStorePassword
    ?: throw GradleException("Release signing.properties is required for release/playstore artifacts")
val Project.releaseKeyAlias: String get() = releaseSigningProperties()?.keyAlias
    ?: throw GradleException("Release signing.properties is required for release/playstore artifacts")
val Project.releaseKeyPassword: String get() = releaseSigningProperties()?.keyPassword
    ?: throw GradleException("Release signing.properties is required for release/playstore artifacts")
val Project.hasReleaseKeyStore: Boolean get() = releaseSigningProperties() != null

private fun signingProperties(rootDir: File): Properties {
    val properties = Properties()
    val signingProperties = rootDir.resolve("signing.properties")
    if (signingProperties.isFile) {
        signingProperties.inputStream().use(properties::load)
    }

    return properties
}
