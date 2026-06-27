plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kian.perficon.templateicons2"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

configureStaticIconPlaceholders(startIndex = 20001, endIndex = 40000)
