plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "it.unisannio.muses"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.unisannio.muses"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = "MuSES.apk"
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
    // Core Android Jetpack libraries
    implementation(libs.androidx.core.ktx) // Kotlin extensions for core Android functionalities
    implementation(libs.androidx.lifecycle.runtime.ktx) // Lifecycle-aware components with Kotlin extensions
    implementation(libs.androidx.activity.compose) // Integration of Jetpack Compose with Activities

    // Jetpack Compose UI Toolkit
    implementation(platform(libs.androidx.compose.bom)) // Bill of Materials for managing Compose dependencies
    implementation(libs.androidx.ui) // Core Compose UI library
    implementation(libs.androidx.ui.graphics) // Graphics and drawing utilities for Compose
    implementation(libs.androidx.ui.tooling.preview) // Support for Composable previews in Android Studio
    implementation(libs.androidx.material3) // Material Design 3 components for Compose

    // Firebase
    implementation(libs.firebase.messaging) // Firebase Cloud Messaging for push notifications

    // Google Play Services
    implementation(libs.play.services.location) // Location services from Google Play Services

    // Networking
    implementation(libs.retrofit) // Type-safe HTTP client for Android and Java
    implementation(libs.converter.gson) // Gson converter for Retrofit to handle JSON
    implementation("com.squareup.picasso:picasso:2.8") // Image loading library

    // Official Mapbox SDK
    implementation(libs.mapbox.maps)

    implementation(libs.androidx.appcompat) // AppCompat library for Android
    implementation(libs.material) // Material design components for Android
    implementation(libs.volley) // Volley library for making network requests
    implementation(libs.androidx.swiperefreshlayout)

    // Camera and QR Code Scanning
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.camera:camera-extensions:1.3.1")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // QR Code Generation
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Testing - Unit Tests
    testImplementation(libs.junit) // JUnit framework for unit testing

    // Testing - Android Instrumented Tests
    androidTestImplementation(libs.androidx.junit) // AndroidX Test library for JUnit
    androidTestImplementation(libs.androidx.espresso.core) // Espresso UI testing framework
    androidTestImplementation(platform(libs.androidx.compose.bom)) // BOM for Compose testing dependencies
    androidTestImplementation(libs.androidx.ui.test.junit4) // Jetpack Compose testing utilities for JUnit4

    // Debugging - Tooling for Development
    debugImplementation(libs.androidx.ui.tooling) // Tools for inspecting and debugging Compose UIs
    debugImplementation(libs.androidx.ui.test.manifest) // Test manifest for Compose UI tests
}

