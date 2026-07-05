// ── App-level build.gradle.kts ────────────────────────────────────────────────
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ✅ Required since Kotlin 2.0 for Compose
    alias(libs.plugins.kotlin.compose)
    // ✅ KSP (not KAPT) for Room + Hilt annotation processing
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ── Read signing credentials from local.properties (never commit this file) ──
//
// Add these lines to your local.properties:
//   KEYSTORE_PATH=../keystore/pocketmind-release.jks
//   KEYSTORE_PASSWORD=yourStorePass
//   KEY_ALIAS=pocketmind
//   KEY_PASSWORD=yourKeyPass
//
// To generate a keystore (run once in terminal):
//   keytool -genkeypair -v -storetype PKCS12 \
//     -keystore pocketmind-release.jks \
//     -alias pocketmind -keyalg RSA -keysize 2048 \
//     -validity 10000
//
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

android {
    namespace  = "com.pocketmind"
    compileSdk = 35

    assetPacks.add(":model_pack")

    defaultConfig {
        applicationId = "com.pocketmind"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        // Limit to 64-bit ABIs — required for Play Store 64-bit policy
        // and ensures MediaPipe native libs are packaged correctly.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // ── Signing ───────────────────────────────────────────────────────────────

    signingConfigs {
        // release config is only wired up when local.properties has the key.
        // CI/CD: supply env vars and override here, or use Play App Signing.
        val keystorePath = localProps.getProperty("KEYSTORE_PATH")
        if (keystorePath != null) {
            create("release") {
                storeFile     = file(keystorePath)
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD") ?: ""
                keyAlias      = localProps.getProperty("KEY_ALIAS") ?: ""
                keyPassword   = localProps.getProperty("KEY_PASSWORD") ?: ""
            }
        }
    }

    // ── Build types ───────────────────────────────────────────────────────────

    buildTypes {
        debug {
            isDebuggable    = true
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
        release {
            isMinifyEnabled   = true   // R8 code shrinking + obfuscation
            isShrinkResources = true   // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Wire release signing config if credentials were found above
            val releaseCfg = signingConfigs.findByName("release")
            if (releaseCfg != null) signingConfig = releaseCfg
        }
    }

    // Required for Room schema export
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildFeatures {
        compose      = true
        buildConfig  = true   // Enables BuildConfig.VERSION_NAME in Kotlin
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // ── Bundle / APK packaging ────────────────────────────────────────────────

    bundle {
        // AAB (Android App Bundle) splits by language, density and ABI.
        // For Play Store always upload AAB, not APK.
        language   { enableSplit = true  }
        density    { enableSplit = true  }
        abi        { enableSplit = true  }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false  // Required for 16 KB page-aligned .so files
        }
        resources {
            // Strip duplicate license files that cause merge conflicts
            excludes += listOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    // ── Compose ───────────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.ext)
    implementation(libs.compose.activity)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // ── Room ──────────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Core ──────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.core.splashscreen)
    // Enables Play-delivered cloud baseline profiles → faster cold start / less JIT jank
    implementation(libs.androidx.profileinstaller)

    // ── Play Store APIs ───────────────────────────────────────────────────────
    implementation(libs.play.review)
    implementation(libs.play.update)

    // ── LiteRT-LM (on-device inference — replaces MediaPipe) ─────────────────
    implementation(libs.litertlm.android)

    // Force 16 KB aligned graphics-path library version (required for Android 15+)
    implementation(libs.androidx.graphics.path)
}
