import java.security.SecureRandom

plugins {
    alias(libs.plugins.self.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dergoogler.mmrl.platform"
    compileSdk = COMPILE_SDK
    ndkVersion = NDK_VERSION

    publishing {
        singleVariant("release")
    }

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += arrayOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
            }
        }
        val aidlDir = file("src/main/aidl")
        aidlPackagedList += aidlDir.walkTopDown()
            .filter { it.isFile && it.extension == "aidl" }
            .map { it.relativeTo(aidlDir).path }
            .toList()
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    splits {
        abi {
            isEnable = false
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    testImplementation(libs.junit)
    compileOnly(projects.hiddenApi)
    implementation(projects.ext)
    implementation(projects.compat)
    implementation(libs.kotlin.parcelize.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.hiddenApiBypass)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.apache.commons.compress)
    implementation(libs.square.retrofit.moshi)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.square.moshi)
    ksp(libs.square.moshi.kotlin)
}

// AGP 9.0 applies KGP internally without going through Gradle's plugin manager, which prevents
// KotlinCompilerPluginSupportPlugin.applyToCompilation() from being called for kotlin.parcelize.
// Use configurations.all (not afterEvaluate + configurations.names) so all variant configs are covered.
val parcelizeVersion = libs.versions.kotlin.get()
configurations.all {
    if (name.startsWith("kotlinCompilerPluginClasspath")) {
        project.dependencies.add(name, "org.jetbrains.kotlin:kotlin-parcelize-compiler:$parcelizeVersion")
    }
}

fun generateRandomName(
    minLength: Int = 5,
    maxLength: Int = 12,
): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val random = SecureRandom()
    val length = random.nextInt(maxLength - minLength + 1) + minLength
    return (1..length)
        .map { chars[random.nextInt(chars.length)] }
        .joinToString("")
}

val testNativeContracts = tasks.register<Exec>("testNativeContracts") {
    group = "verification"
    description = "Build and run host-side native contract tests for the safe file-manager and KernelSU JNI bridges."
    val buildDir = layout.buildDirectory.dir("native-contract-tests").get().asFile
    val hostCompiler = providers.environmentVariable("MMRL_HOST_CXX").orElse(
        providers.environmentVariable("PREFIX").map { "$it/bin/c++" },
    ).orElse("c++")
    inputs.files(
        fileTree("src/testNative") { include("**/*.cpp") },
        fileTree("src/main/jni") { include("**/*.cpp", "**/*.hpp") },
    )
    inputs.property("hostCompiler", hostCompiler)
    outputs.dir(buildDir)
    doFirst { buildDir.mkdirs() }
    val script = """
        set -euo pipefail
        cxx='${hostCompiler.get()}'
        out='${buildDir.absolutePath}'
        resolved_cxx="$(command -v "${'$'}cxx" || true)"
        if [[ -z "${'$'}resolved_cxx" ]]; then
          echo "MMRL host native contract compiler not found: ${'$'}cxx" >&2
          exit 127
        fi
        case "${'$'}resolved_cxx" in
          *android-ndk*|*/ndk/*|*/toolchains/llvm/prebuilt/*|*aarch64-linux-android*|*armv7a-linux-androideabi*|*x86_64-linux-android*|*i686-linux-android*)
            echo "MMRL host native contracts require a host C++ compiler, not Android NDK CXX: ${'$'}resolved_cxx" >&2
            echo "Set MMRL_HOST_CXX to a host compiler such as /data/data/com.termux/files/usr/bin/c++ if needed." >&2
            exit 2
            ;;
        esac
        cxx="${'$'}resolved_cxx"
        "${'$'}cxx" -std=c++17 -DMMRL_HOST_NATIVE_CONTRACT -Wall -Wextra -Werror \
          -Isrc/main/jni/include \
          src/testNative/file_manager_safe_contract_test.cpp \
          src/main/jni/file-manager.cpp \
          -o "${'$'}out/file_manager_safe_contract_test"
        "${'$'}out/file_manager_safe_contract_test"

        "${'$'}cxx" -std=c++17 -DMMRL_HOST_NATIVE_CONTRACT -Wall -Wextra -Werror \
          -Isrc/main/jni/include \
          src/testNative/kernelsu/ksu_compat_contract_test.cpp \
          src/main/jni/kernelsu/ksu.cpp \
          -o "${'$'}out/ksu_compat_contract_test"
        "${'$'}out/ksu_compat_contract_test"
    """.trimIndent()
    commandLine("bash", "-lc", script)
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(testNativeContracts)
}
