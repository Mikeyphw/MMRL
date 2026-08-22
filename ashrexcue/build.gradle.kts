plugins {
    alias(libs.plugins.self.application)
    alias(libs.plugins.self.compose)
}

val ashReXcueApplicationId = "com.mikeyphw.ashrexcue"

android {
    namespace = ashReXcueApplicationId

    defaultConfig {
        applicationId = ashReXcueApplicationId
        versionCode = 1
        versionName = "0.1.0-split-foundation"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("official") {
            dimension = "distribution"
            resValue("string", "app_name", "AshReXcue")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "AshReXcue Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            versionNameSuffix = "-release"
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
