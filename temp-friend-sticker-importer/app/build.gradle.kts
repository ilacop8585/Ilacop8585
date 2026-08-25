plugins {
    id("com.android.application")
}

android {
    namespace = "it.nexus.friendstickers"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.nexus.friendstickers"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-test"
        buildConfigField("String", "CONTENT_PROVIDER_AUTHORITY", "\"it.nexus.friendstickers.stickercontentprovider\"")
    }

    buildFeatures {
        buildConfig = true
    }
}
