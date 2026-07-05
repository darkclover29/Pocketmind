// ── Asset pack build.gradle.kts ───────────────────────────────────────────────
plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName = "model_pack"
    dynamicDelivery {
        deliveryType = "install-time"
    }
}
