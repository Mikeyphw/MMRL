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
