plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * Copies the built template APK to the main app's assets directory so
 * ApkGenerator can pick it up at runtime.
 *
 * Usage:  ./gradlew :template-app:assembleRelease copyTemplateApk
 *          ./gradlew :app:assembleDebug   (template must already be copied)
 */
tasks.register<Copy>("copyTemplateApk") {
    dependsOn(":template-app:assembleRelease")
    from(project(":template-app").layout.buildDirectory.file("outputs/apk/release/template-app-release.apk"))
    into(project(":app").projectDir.resolve("src/main/assets"))
    rename { "base.apk" }
    doLast { println("√ base.apk updated from template-app release build.") }
}