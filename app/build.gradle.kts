/**
 * Module-level Gradle build script (Kotlin DSL) for the SafetYSec app.
 *
 * Configures:
 * - Android application + Kotlin + Compose
 * - Firebase (Google Services + BoM dependencies)
 * - Coroutines Task.await() support (Play Services)
 * - Hilt dependency injection (plugin + kapt compiler)
 * - Basic test dependencies
 */
plugins {
    // Android / Kotlin / Compose plugins via version catalog aliases
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Required for Firebase configuration via google-services.json
    id("com.google.gms.google-services")

    // Hilt DI plugin (generates required components)
    id("com.google.dagger.hilt.android")

    // Kotlin annotation processing (needed for Hilt compiler)
    kotlin("kapt")
}

android {
    /** Application namespace (also used as the package for generated R classes). */
    namespace = "pt.a2025121082.isec.safetysec"

    /** Compile SDK version used to build the app. */
    compileSdk = 36

    defaultConfig {
        /** Unique application id (package name used on the device/store). */
        applicationId = "pt.a2025121082.isec.safetysec"

        /** Minimum supported Android version. */
        minSdk = 24

        /** Target Android version (behavior compatibility target). */
        targetSdk = 36

        /** Internal version code (must increase with each release). */
        versionCode = 1

        /** User-visible version name. */
        versionName = "1.0"

        /** Instrumentation runner used for Android UI tests. */
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            /**
             * Code shrinking/obfuscation with R8/ProGuard.
             * Disabled here for simplicity; enable for production releases.
             */
            isMinifyEnabled = false

            /** ProGuard/R8 configuration files. */
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        /** Java language level for source and target compatibility. */
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        /** Kotlin JVM target version. */
        jvmTarget = "11"
    }

    buildFeatures {
        /** Enables Jetpack Compose build features. */
        compose = true
    }
}

dependencies {
    // Material icons + Material3 UI components
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.material3)

    // Core AndroidX libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Firebase (BoM + non-KTX artifacts)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Enables Task.await() for Firebase/Play Services tasks in coroutines
    implementation(libs.kotlinx.coroutines.play.services)

    // Hilt dependency injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Unit testing
    testImplementation(libs.junit)

    // Android instrumentation testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}