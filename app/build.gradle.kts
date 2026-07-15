

import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    alias(libs.plugins.self.application)
    alias(libs.plugins.self.compose)
    alias(libs.plugins.self.hilt)
    alias(libs.plugins.self.room)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val baseAppName = "MMRL Fork"
val mmrlSourceNamespace = "com.dergoogler.mmrl"
val mmrlForkApplicationId = "com.mikeyphw.mmrl"
val mmrlWebUiPermissionId = "com.dergoogler.mmrl"

val appVersion = commitCount + 31320

android {
    compileSdk = COMPILE_SDK
    namespace = mmrlSourceNamespace

    defaultConfig {
        applicationId = mmrlForkApplicationId
        versionName = "v$appVersion"
        versionCode = appVersion

        androidResources.localeFilters += arrayOf(
            "en",
            "ar",
            "de",
            "es",
            "fr",
            "hi",
            "in",
            "it",
            "ja",
            "ta",
            "pl",
            "pt",
            "ro",
            "ru",
            "tr",
            "vi",
            "zh-rCN",
            "zh-rTW"
        )
    }

    val releaseSigning = if (project.hasReleaseKeyStore) {
        signingConfigs.create("release") {
            storeFile = project.releaseKeyStore
            storePassword = project.releaseKeyStorePassword
            keyAlias = project.releaseKeyAlias
            keyPassword = project.releaseKeyPassword
            enableV2Signing = true
            enableV3Signing = false
        }
    } else {
        signingConfigs.getByName("debug")
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("official") {
            dimension = "distribution"
            applicationId = mmrlForkApplicationId
            resValue("string", "app_name", baseAppName)
            buildConfigField("Boolean", "IS_SPOOFED_BUILD", "false")
        }

        create("spoofed") {
            dimension = "distribution"
            applicationId = generateRandomPackageName()
            resValue("string", "app_name", generateRandomName())
            buildConfigField("Boolean", "IS_SPOOFED_BUILD", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", baseAppName)
            buildConfigField("Boolean", "IS_DEV_VERSION", "false")
            buildConfigField("Boolean", "IS_GOOGLE_PLAY_BUILD", "false")
            isDebuggable = false
            isJniDebuggable = false
            versionNameSuffix = "-release"
            renderscriptOptimLevel = 3

            manifestPlaceholders["webuiPermissionId"] = mmrlWebUiPermissionId
        }

        create("playstore") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("debug", "release")
            buildConfigField("Boolean", "IS_GOOGLE_PLAY_BUILD", "true")
            versionNameSuffix = "-playstore"
        }

        debug {
            resValue("string", "app_name", "$baseAppName Debug")
            buildConfigField("Boolean", "IS_DEV_VERSION", "true")
            buildConfigField("Boolean", "IS_GOOGLE_PLAY_BUILD", "false")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isJniDebuggable = true
            isDebuggable = true
            renderscriptOptimLevel = 0
            isMinifyEnabled = false

            manifestPlaceholders["webuiPermissionId"] = "$mmrlWebUiPermissionId.debug"
        }

        all {
            signingConfig = releaseSigning

            buildConfigField("String", "COMPILE_SDK", "\"$COMPILE_SDK\"")
            buildConfigField("String", "TARGET_SDK", "\"$TARGET_SDK\"")
            buildConfigField("String", "BUILD_TOOLS_VERSION", "\"${BUILD_TOOLS_VERSION}\"")
            buildConfigField("String", "MIN_SDK", "\"$MIN_SDK\"")
            buildConfigField("String", "NDK_VERSION", "\"$NDK_VERSION\"")
            buildConfigField("String", "LATEST_COMMIT_ID", "\"${commitId}\"")

            manifestPlaceholders["__packageName__"] = mmrlSourceNamespace
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("lib/arm64-v8a/libmmrl-file-manager.so")
        }

        resources {
            pickFirsts += setOf(
                "META-INF/gradle/incremental.annotation.processors"
            )

            excludes += setOf(
                "okhttp3/**",
                // "kotlin/**",
                "org/**",
                "**.properties",
                "**.bin",
                "**/*.proto"
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

}


androidComponents {
    onVariants { variant ->
        variant.outputs.filterIsInstance<VariantOutputImpl>().forEach { output ->
            output.outputFileName.set(
                output.versionName.map { vName ->
                    "MMRL-Fork-${vName}-${variant.buildType ?: variant.name}.apk"
                }
            )
        }
    }
    onVariants(selector().withBuildType("release")) {
        it.packaging.resources.excludes.add("META-INF/**")
    }
}

dependencies {
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
    testImplementation(libs.junit)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.swiperefreshlayout)
    compileOnly(projects.hiddenApi)
    implementation(projects.platform)
    implementation(projects.ui)
    implementation(projects.ext)
    implementation(projects.compat)
    implementation(projects.datastore)

    implementation(libs.webuix.hwui)
    implementation(libs.webuix.helper)

    implementation(libs.kotlin.stdlib)

    implementation(libs.hiddenApiBypass)
    // implementation(libs.timber)
    implementation(libs.arbor.jvm)
    implementation(libs.arbor.android)

    implementation(libs.semver)
    implementation(libs.coil.compose)

    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.io)

    implementation(libs.rikka.refine.runtime)
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)

    implementation(libs.apache.commons.compress)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewModel.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.reflect)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.multiplatform.markdown.renderer.android)
    implementation(libs.multiplatform.markdown.renderer.coil3)
    implementation(libs.dev.rikka.rikkax.parcelablelist)
    implementation(libs.lib.zoomable)
    implementation(libs.process.phoenix)
    // implementation(libs.androidx.adaptive)
    // implementation(libs.androidx.adaptive.android)
    // implementation(libs.androidx.adaptive.layout)
    // implementation(libs.androidx.adaptive.navigation)
    implementation(libs.kotlinx.html.jvm)

    implementation(libs.square.retrofit)
    implementation(libs.square.retrofit.moshi)
    implementation(libs.square.retrofit.kotlinxSerialization)
    implementation(libs.square.okhttp)
    implementation(libs.square.okhttp.dnsoverhttps)
    implementation(libs.square.logging.interceptor)
    implementation(libs.square.moshi)
    ksp(libs.square.moshi.kotlin)

    implementation(project(":terminal-compat"))
    implementation(project(":webui-core-compat"))

    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation("dev.chrisbanes.haze:haze-materials:1.6.10")

    implementation(libs.composedestinations.core)
    ksp(libs.composedestinations.ksp)

    implementation(libs.kotlin.parcelize.runtime)
}

// AGP 9.0 applies KGP internally without triggering KotlinCompilerPluginSupportPlugin.applyToCompilation(),
// so kotlin-parcelize-compiler is never added to the kotlinc plugin classpath. Use configurations.all
// (not afterEvaluate + configurations.names) so flavor-specific configs are also covered when created lazily.
val parcelizeVersion = libs.versions.kotlin.get()
configurations.all {
    if (name.startsWith("kotlinCompilerPluginClasspath")) {
        project.dependencies.add(name, "org.jetbrains.kotlin:kotlin-parcelize-compiler:$parcelizeVersion")
    }
}

tasks.register("version") {
    println(appVersion)
}
