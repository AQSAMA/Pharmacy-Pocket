plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aqsama.pharmacypocket"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.aqsama.pharmacypocket"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 30_000_000
        versionName = "3.0.0-native"
    }

    signingConfigs {
        create("preview") {
            // Intentionally public test key. Never use this identity for production.
            storeFile = rootProject.file("signing/preview.jks")
            storePassword = "pharmacy-pocket-preview"
            keyAlias = "preview"
            keyPassword = "pharmacy-pocket-preview"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("preview") {
            dimension = "distribution"
            applicationIdSuffix = ".native"
            versionNameSuffix = "-preview"
            resValue("string", "app_name", "Pharmacy Pocket Native")
            signingConfig = signingConfigs.getByName("preview")
        }
        create("production") {
            dimension = "distribution"
            resValue("string", "app_name", "Pharmacy Pocket")
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.12.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
