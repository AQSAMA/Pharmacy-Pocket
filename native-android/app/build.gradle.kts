plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val pharmacyVersion = rootProject.file("VERSION").readText().trim()
val versionMatch = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(pharmacyVersion)
    ?: throw GradleException("VERSION must contain MAJOR.MINOR.PATCH, for example 3.0.0.")
val (majorText, minorText, patchText) = versionMatch.destructured
val versionMajor = majorText.toLong()
val versionMinor = minorText.toLong()
val versionPatch = patchText.toLong()
if (versionMinor > 999L || versionPatch > 9_999L) {
    throw GradleException("VERSION minor must be <= 999 and patch must be <= 9999.")
}
val computedVersionCodeLong = versionMajor * 10_000_000L + versionMinor * 10_000L + versionPatch
if (computedVersionCodeLong !in 1L..2_100_000_000L) {
    throw GradleException("VERSION produces an Android versionCode outside the supported range.")
}
val computedVersionCode = computedVersionCodeLong.toInt()
val buildVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: computedVersionCode
if (buildVersionCode !in 1..2_100_000_000) {
    throw GradleException("versionCode must be between 1 and 2100000000.")
}

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() }
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val releaseSigningMissing = buildList {
    if (releaseKeystorePath == null) add("ANDROID_KEYSTORE_PATH")
    if (releaseKeystorePassword == null) add("ANDROID_KEYSTORE_PASSWORD")
    if (releaseKeyAlias == null) add("ANDROID_KEY_ALIAS")
    if (releaseKeyPassword == null) add("ANDROID_KEY_PASSWORD")
}

android {
    namespace = "com.aqsama.pharmacypocket"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.aqsama.pharmacypocket"
        minSdk = 29
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = pharmacyVersion
    }

    signingConfigs {
        create("preview") {
            // Intentionally public preview-only key. Never use for production.
            storeFile = rootProject.file("signing/preview.jks")
            storePassword = "pharmacy-pocket-preview"
            keyAlias = "preview"
            keyPassword = "pharmacy-pocket-preview"
        }
        create("release") {
            storeFile = file(releaseKeystorePath ?: "missing-release-keystore")
            storePassword = releaseKeystorePassword ?: ""
            keyAlias = releaseKeyAlias ?: ""
            keyPassword = releaseKeyPassword ?: ""
        }
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    buildFeatures {
        compose = true
        resValues = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Pharmacy Pocket Dev")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            resValue("string", "app_name", "Pharmacy Pocket")
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".native"
            versionNameSuffix = "-preview"
            resValue("string", "app_name", "Pharmacy Pocket Preview")
            signingConfig = signingConfigs.getByName("preview")
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
            "/META-INF/NOTICE*",
        )
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (releaseSigningMissing.isNotEmpty()) {
            throw GradleException(
                "Release signing is required. Missing environment variables: ${releaseSigningMissing.joinToString(", ")}",
            )
        }
        val keystore = file(requireNotNull(releaseKeystorePath))
        if (!keystore.isFile) {
            throw GradleException("Release signing is required: ANDROID_KEYSTORE_PATH does not point to a readable file.")
        }
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
    testImplementation("androidx.compose.ui:ui-test-junit4:1.12.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
