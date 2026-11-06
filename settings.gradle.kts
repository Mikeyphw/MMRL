enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val user: String? = providers.gradleProperty("gpr.user").orNull
    ?: System.getenv("ACTOR")
val pass: String? = providers.gradleProperty("gpr.key").orNull
    ?: System.getenv("GH_TOKEN")

dependencyResolutionManagement drm@{
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")

        if (user != null && pass != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/MMRLApp/X")
                credentials {
                    username = user
                    password = pass
                }
            }
        }
    }
}

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "MMRL"
include(
    ":app",
    ":hidden-api",
    ":platform",
    ":ui",
    ":ext",
    ":datastore"
)
include(":terminal-compat", ":webui-core-compat", ":compat")

// N05C: source-locked, non-privileged Kotlin contracts; not an upstream/LSPosed merge.
include(":shared-contracts")
project(":shared-contracts").projectDir = file("integration/shared-contracts")
