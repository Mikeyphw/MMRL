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
    "androidGradlePlugin" to "9.3.2",
    "kotlin" to "2.4.10",
    "kotlinReflect" to "2.4.10",
    "ksp" to "2.3.11",
    "hilt" to "2.60.1",
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
    description = "Verifies MMRL's normalized stable Android/JVM/native toolchain baseline."

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
            "const val NDK_VERSION = \"29.0.14206865\"",
        ).forEach { required ->
            check(required in projectExt) { "MMRL normalized Android/native baseline is missing: $required" }
        }

        val devtool = file(".devtool.toml").readText()
        listOf(
            "version = \"9.7.1\"",
            "provider = \"wrapper\"",
            "ndk_version = \"29.0.14206865\"",
            "memory_guard_mb = 0",
            "parallel = false",
        ).forEach { required ->
            check(required in devtool) { "MMRL Devtool baseline is missing: $required" }
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
                check("JavaVersion.VERSION_21" in body) { "$relativePath must target Java 21" }
            }
        }
    }
}

/**
 * MMRL is one product. Seal the repository by allowlisting the modules and
 * package roots which belong to that product instead of encoding knowledge of
 * any external or previously co-located application.
 */
tasks.register("verifyMmrlProductBoundary") {
    group = "verification"
    description = "Verifies that MMRL contains only its declared Gradle modules and production package roots."

    doLast {
        val allowedProjects = setOf(
            ":app",
            ":hidden-api",
            ":platform",
            ":ui",
            ":ext",
            ":datastore",
            ":terminal-compat",
            ":webui-core-compat",
            ":compat",
        )
        val actualProjects = subprojects.map { it.path }.toSet()
        check(actualProjects == allowedProjects) {
            "Unexpected MMRL Gradle project set. Expected $allowedProjects, found $actualProjects"
        }

        val allowedTopLevelBuilds = allowedProjects.map { it.removePrefix(":") }.toSet() + "build-logic"
        val topLevelBuilds = rootDir.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "build.gradle.kts").isFile }
            .map { it.name }
            .toSet()
        check(topLevelBuilds == allowedTopLevelBuilds) {
            "Unexpected top-level Gradle modules. Expected $allowedTopLevelBuilds, found $topLevelBuilds"
        }

        val appMain = file("app/src/main")
        val allowedMainEntries = setOf("AndroidManifest.xml", "assets", "java", "kotlin", "res")
        val actualMainEntries = appMain.listFiles().orEmpty()
            .filter { entry -> entry.isFile || entry.walkTopDown().any { it.isFile } }
            .map { it.name }
            .toSet()
        check(actualMainEntries.all { it in allowedMainEntries }) {
            "Unexpected non-empty app/src/main entries: ${actualMainEntries - allowedMainEntries}"
        }

        val packageRoot = file("app/src/main/kotlin/com/dergoogler/mmrl")
        val allowedPackageRoots = setOf(
            "app", "database", "datastore", "debug", "github", "installer", "lsposed",
            "model", "network", "operation", "pathHandler", "receiver", "repository",
            "service", "stub", "tasker", "ui", "utils", "viewmodel",
        )
        val actualPackageRoots = packageRoot.listFiles().orEmpty()
            .filter { it.isDirectory }
            .map { it.name }
            .toSet()
        check(actualPackageRoots.all { it in allowedPackageRoots }) {
            "Unexpected MMRL production package roots: ${actualPackageRoots - allowedPackageRoots}"
        }

        val aidlFiles = fileTree("app/src/main") { include("**/*.aidl") }.files
        check(aidlFiles.isEmpty()) { "MMRL app must not carry undeclared app-level AIDL sources: $aidlFiles" }

        val appBuild = file("app/build.gradle.kts").readText()
        check("aidl = false" in appBuild) { "MMRL app must keep unused AIDL generation disabled" }
        check("create(\"playstore\")" !in appBuild.lowercase()) { "MMRL personal-use build must not define a store flavor" }

        val settings = file("settings.gradle.kts").readText()
        check("includeBuild(\"build-logic\")" in settings) { "MMRL must keep its local convention-plugin build" }

        val applicationModules = subprojects.filter { project ->
            project.plugins.hasPlugin("com.android.application")
        }.map { it.path }
        check(applicationModules == listOf(":app")) {
            "MMRL must expose exactly one Android application module, found $applicationModules"
        }
    }
}

private val mmrlRepositoryHygieneExcludedRoots = setOf(
    ".git", ".gradle", ".idea", ".kotlin", ".devtool", "build", "build-logs",
)

/**
 * Reject source-tree backup/scratch artifacts which can silently revive obsolete build logic.
 * Runtime Devtool state is intentionally excluded because it is local execution metadata.
 */
tasks.register("verifyRepositoryHygiene") {
    group = "verification"
    description = "Verifies that the MMRL source tree contains no stale backup/generated release artifacts."

    doLast {
        val forbiddenSuffixes = listOf(".bak", ".orig", ".rej", "~")
        val forbiddenMarkers = listOf(".before-")
        val forbiddenExtensions = setOf("apk", "aab", "aar", "ap_", "idsig", "hprof")

        val offenders = rootDir.walkTopDown()
            .onEnter { dir ->
                dir == rootDir || dir.name !in mmrlRepositoryHygieneExcludedRoots
            }
            .filter { it.isFile }
            .filter { file ->
                val name = file.name
                forbiddenSuffixes.any(name::endsWith) ||
                    forbiddenMarkers.any(name::contains) ||
                    file.extension.lowercase() in forbiddenExtensions
            }
            .map { it.relativeTo(rootDir).invariantSeparatorsPath }
            .toList()

        check(offenders.isEmpty()) {
            "MMRL source tree contains stale backup/generated artifacts: $offenders"
        }
    }
}

/**
 * One authoritative host-side MMRL release contract. The shell release runner invokes
 * the same tasks in memory-bounded phases on Termux; CI may invoke this aggregate task.
 * Device-backed validation is deliberately separate and optional.
 */
tasks.register("mmrlReleaseSeal") {
    group = "verification"
    description = "Run the complete host-side MMRL personal-use release seal."
    dependsOn(
        "verifyStableToolchainBaseline",
        "verifyMmrlProductBoundary",
        "verifyRepositoryHygiene",
        ":platform:testDebugUnitTest",
        ":platform:testNativeContracts",
        ":app:testOfficialDebugUnitTest",
        ":app:fullLintOfficialDebug",
        ":app:compileOfficialDebugAndroidTestKotlin",
        ":app:processOfficialDebugResources",
        ":app:verifyReleaseArtifacts",
    )
}

tasks.register("mmrlDeviceValidation") {
    group = "verification"
    description = "Run optional connected-device instrumentation after the host release seal passes."
    dependsOn(":app:connectedOfficialDebugAndroidTest")
}

