// ── Project-level build.gradle.kts ───────────────────────────────────────────
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.kotlin.android)       apply false
    // ✅ Required since Kotlin 2.0 for Compose
    alias(libs.plugins.kotlin.compose)       apply false
    // ✅ KSP instead of KAPT — dramatically faster incremental builds
    alias(libs.plugins.ksp)                  apply false
    // Hilt
    alias(libs.plugins.hilt)                 apply false
    // Play Asset Delivery
    alias(libs.plugins.android.asset.pack)    apply false
}
