// Pure MMRL-Shared Kotlin sources from an exact gitlink: no source copies or Maven publication.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.mikeyphw.mmrl.shared.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    // AGP 9 built-in Kotlin (validated in MMRL-LSPosed N05B).
    // Register the pinned .kt sources as Kotlin, NOT as a Java source root.
    sourceSets.named("main") {
        kotlin.directories.add(
            rootProject.file("third_party/mmrl-shared/src/main/kotlin").absolutePath
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
