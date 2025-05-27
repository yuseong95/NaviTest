
import com.android.build.api.dsl.Packaging

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}



android {
    namespace = "com.capstone.navitest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.capstone.navitest"
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 여기에 추가



        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }



  /*  externalNativeBuild {
        cmake {
            // ChatApp 모듈의 CMakeLists.txt 경로
            path = file("../ChatApp/src/main/cpp/CMakeLists.txt")
        }
    }*/

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
}





dependencies {
    // ChatApp, Whisper 모듈
    implementation(project(":whisper"))
    implementation(project(":ChatApp"))

    // Mapbox Navigation
    implementation("com.mapbox.navigationcore:android:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:copilot:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:ui-maps:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:voice:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:tripdata:3.9.0-rc.1")
    implementation("com.mapbox.navigationcore:ui-components:3.9.0-rc.1")

    // Mapbox Search
    implementation("com.mapbox.search:place-autocomplete:2.12.0-beta.1")
    implementation("com.mapbox.search:mapbox-search-android-ui:2.12.0-beta.1")

    // Android UI
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

