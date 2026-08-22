plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
    delete(subprojects.map { it.layout.buildDirectory })
}

val stableToolchainVersions = mapOf(
    "androidGradlePlugin" to "9.3.1",
    "kotlin" to "2.4.10",
    "kotlinReflect" to "2.4.10",
    "ksp" to "2.3.11",
)

val stableVersionCatalog = extensions
    .getByType<org.gradle.api.artifacts.VersionCatalogsExtension>()
    .named("libs")

val prereleaseVersionPattern = Regex(
    pattern = "(?:^|[-._])(?:alpha|beta|rc|cr|preview|canary|eap|snapshot|nightly|milestone|m\\d+)(?:[-._0-9]|$)",
    option = RegexOption.IGNORE_CASE,
)

tasks.register("verifyStableToolchainBaseline") {
    group = "verification"
    description = "Verifies MMRL's normalized stable Android/JVM toolchain baseline."

    doLast {
        check(gradle.gradleVersion == "9.7.1") {
            "MMRL requires Gradle 9.7.1, found ${gradle.gradleVersion}"
        }
        check(JavaVersion.current() == JavaVersion.VERSION_21) {
            "MMRL requires Gradle to run on Java 21, found ${JavaVersion.current()}"
        }

        stableToolchainVersions.forEach { (alias, expected) ->
            val actual = stableVersionCatalog.findVersion(alias)
                .orElseThrow { GradleException("Missing version catalog alias: $alias") }
                .requiredVersion
            check(actual == expected) {
                "MMRL requires $alias $expected, found $actual"
            }
        }

        stableVersionCatalog.versionAliases.forEach { alias ->
            val version = stableVersionCatalog.findVersion(alias).get().requiredVersion
            check(!prereleaseVersionPattern.containsMatchIn(version)) {
                "MMRL stable-only policy rejects prerelease version $alias=$version"
            }
        }

        val projectExt = file("build-logic/src/main/kotlin/ProjectExt.kt").readText()
        listOf(
            "const val COMPILE_SDK = 36",
            "const val TARGET_SDK = 36",
            "const val BUILD_TOOLS_VERSION = \"36.0.0\"",
        ).forEach { required ->
            check(required in projectExt) { "MMRL normalized Android baseline is missing: $required" }
        }

        val java21Sources = listOf(
            "build-logic/src/main/kotlin/ApplicationConventionPlugin.kt",
            "build-logic/src/main/kotlin/LibraryConventionPlugin.kt",
            "app/build.gradle.kts",
            "compat/build.gradle.kts",
            "datastore/build.gradle.kts",
            "ext/build.gradle.kts",
            "hidden-api/build.gradle.kts",
            "platform/build.gradle.kts",
            "terminal-compat/build.gradle.kts",
            "ui/build.gradle.kts",
            "webui-core-compat/build.gradle.kts",
        )
        java21Sources.forEach { relativePath ->
            val body = file(relativePath).readText()
            check("JavaVersion.VERSION_11" !in body && "JavaVersion.VERSION_17" !in body) {
                "$relativePath still contains a pre-Java-21 compatibility target"
            }
            if ("sourceCompatibility" in body || "targetCompatibility" in body) {
                check("JavaVersion.VERSION_21" in body) {
                    "$relativePath must target Java 21"
                }
            }
        }
    }
}


/**
 * Split OV09 regression gate: MMRL must not contain or compile against the
 * embedded AshReXcue implementation. Module-manager snapshots remain owned by
 * MMRL and are intentionally verified separately.
 */
tasks.register("verifyAshReXcuePurgedFromMmrl") {
    group = "verification"
    description = "Verifies that embedded AshReXcue runtime/UI code is absent while MMRL module snapshots remain intact."

    doLast {
        val forbiddenPaths = listOf(
            "ashrexcue",
            "app/src/main/ash-module",
            "app/src/main/aidl/com/dergoogler/mmrl/ash",
            "app/src/main/kotlin/com/dergoogler/mmrl/ash",
            "app/src/main/kotlin/com/dergoogler/mmrl/tasker/TaskerAshActions.kt",
            "app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/home/items/AshProtectionCard.kt",
            "app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/moduleView/sections/AshModuleIntelligenceCard.kt",
            "app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/moduleView/sections/AshReXcueIntegration.kt",
            "app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/bootProtection/BootProtectionScreen.kt",
        )
        val present = forbiddenPaths.filter { relativePath ->
            val candidate = file(relativePath)
            candidate.isFile || (candidate.isDirectory && candidate.walkTopDown().any { it.isFile })
        }
        check(present.isEmpty()) { "Embedded AshReXcue files returned to MMRL: ${present.joinToString()}" }

        val sourceViolations = fileTree("app/src/main") {
            include("**/*.kt", "**/*.java", "**/*.aidl")
        }.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("import com.dergoogler.mmrl.ash") ||
                    trimmed.startsWith("package com.dergoogler.mmrl.ash")) {
                    "${source.relativeTo(rootDir)}:${index + 1}: $trimmed"
                } else null
            }
        }
        check(sourceViolations.isEmpty()) {
            "MMRL still compiles against embedded AshReXcue sources:\n${sourceViolations.joinToString("\n")}" 
        }

        val settings = file("settings.gradle.kts").readText()
        check(":ashrexcue" !in settings) { "MMRL must not include a temporary :ashrexcue project" }
        val appBuild = file("app/build.gradle.kts").readText()
        check("packageAshReXcueModule" !in appBuild && "src/main/ash-module" !in appBuild) {
            "MMRL must not package the AshReXcue recovery module"
        }

        val snapshotModel = file("app/src/main/kotlin/com/dergoogler/mmrl/model/local/ModuleVersionPolicy.kt").readText()
        val snapshotStore = file("app/src/main/kotlin/com/dergoogler/mmrl/repository/ModulePolicyStore.kt").readText()
        check("data class ModuleSnapshot(" in snapshotModel && "object ModuleSnapshotPlanner" in snapshotModel) {
            "MMRL-owned ModuleSnapshot feature must remain present"
        }
        check("suspend fun saveSnapshot(" in snapshotStore) {
            "MMRL-owned module snapshot persistence must remain present"
        }
    }
}
