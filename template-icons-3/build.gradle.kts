plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kian.perficon.templateicons3"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

configureStaticIconPlaceholders(startIndex = 40001, endIndex = 60000)
