plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kian.perficon.templateicons1"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

configureStaticIconPlaceholders(startIndex = 1, endIndex = 20000)
