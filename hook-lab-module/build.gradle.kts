plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mikeyphw.mmrl.hooklab.module"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mikeyphw.mmrl.hooklab.module"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
        getByName("debug") { isMinifyEnabled = false }
    }
    // These non-Android resources must remain in the APK at the exact libxposed paths.
    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
}
