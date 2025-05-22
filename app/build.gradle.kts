
import com.android.build.api.dsl.AndroidResources

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
// Path to local QNN SDK.
// Download QNN SDK from https://qpm.qualcomm.com/#/main/tools/details/qualcomm_ai_engine_direct
val qnnSDKLocalPath = "C:\\Qualcomm\\AIStack\\QAIRT\\2.32.6.250402"

// List of model assets
val models = listOf("llama3_2_3b")
// Relative asset path for model configuration and binaries
val relAssetsPath = "src/main/assets/models/"
val buildDir = project(":app").layout.buildDirectory.getAsFile().get()
val libsDir = File(buildDir, "libs")

android {
    namespace = "com.capstone.navitest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.capstone.navitest"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                abiFilters("arm64-v8a")
                arguments(
                    "-DCMAKE_VERBOSE_MAKEFILE=ON",
                    "-DQNN_SDK_ROOT_PATH=$qnnSDKLocalPath"
                )
            }
        }
        sourceSets {
            getByName("main") {
                jniLibs.srcDir(libsDir)
            }
        }
        signingConfig = signingConfigs.getByName("debug")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        // Extract native libraries so they're accessible via the file system directly.
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += listOf("bin", "json")
        }
    }
}
tasks.named("preBuild") {
    doFirst {
        // Check if QNN SDK is set correctly
        if (!file(qnnSDKLocalPath).exists()) {
            throw RuntimeException("QNN SDK does not exist at $qnnSDKLocalPath. Please set `qnnSDKLocalPath` in build.gradle.kts.")
        }

        // Copy required QNN libs
        if (!file("$qnnSDKLocalPath/lib/aarch64-android/libGenie.so").exists()) {
            throw RuntimeException("libGenie does not exist. Please set `qnnSDKLocalPath` in build.gradle.kts.")
        }

        // Ensure genie-config and tokenizer is present
        models.forEach { model ->
            if (!file("$relAssetsPath$model/genie-config.json").exists()) {
                throw RuntimeException("Missing genie-config.json for $model.")
            }
            if (!file("$relAssetsPath$model/tokenizer.json").exists()) {
                throw RuntimeException("Missing tokenizer.json for $model.")
            }
        }

        val libsABIDir = File(buildDir, "libs/arm64-v8a")
        copy {
            from(qnnSDKLocalPath)
            include("**/lib/aarch64-android/libPenguin.so")
            include("**/lib/aarch64-android/libQnnHtp.so")
            include("**/lib/aarch64-android/libQnnHtpPrepare.so")
            include("**/lib/aarch64-android/libQnnSystem.so")
            include("**/lib/aarch64-android/libQnnSaver.so")
            include("**/lib/hexagon-v**/unsigned/libQnnHtpV**Skel.so")
            include("**/lib/aarch64-android/libQnnHtpV**Stub.so")

            into(libsABIDir)
            // Copy libraries without directory structure
            eachFile {
                path = name
            }
            includeEmptyDirs = false
        }
    }
}


dependencies {

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    // Mapbox Navigation SDK
    implementation("com.mapbox.navigationcore:android:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:copilot:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:ui-maps:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:voice:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:tripdata:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:ui-components:3.9.0-rc.1")

    // AndroidX & Jetpack Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
