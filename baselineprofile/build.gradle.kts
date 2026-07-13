// ── Baseline profile generator + macrobenchmarks for :app ────────────────────
//
// Generate the profile (headless, uses the Gradle-managed emulator below;
// first run downloads the system image):
//
//     gradlew :app:generateBaselineProfile
//
// The result is written to app/src/release/generated/baselineProfiles/ and is
// packaged automatically into release builds. Regenerate before each release.
//
// To measure the effect (needs a physical device or the managed device):
//
//     gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
//
import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace  = "com.pocketshadow.baselineprofile"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    defaultConfig {
        // Baseline profile capture needs API 28+ (33+ recommended, emulators OK)
        minSdk    = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // Headless emulator for one-command generation. "aosp" (no Play services)
    // keeps benchmark results clean and the image download smaller.
    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device         = "Pixel 6"
            apiLevel       = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    // Flip to true (and remove the managed device above if you like) to
    // generate on a plugged-in physical device instead.
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
