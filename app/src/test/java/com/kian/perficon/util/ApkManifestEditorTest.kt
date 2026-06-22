package com.kian.perficon.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ApkManifestEditorTest {

    @Test
    fun patchesTheCandyBarTemplatePackageAndVersionCode() {
        val template = File("src/main/assets/base.apk")
        val originalManifest = ZipFile(template).use { zip ->
            zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readBytes()
        }

        val patched = ApkManifestEditor.patch(
            manifest = originalManifest,
            packageName = "com.perficon.generated.longerpackname",
            versionCode = 1_987_654_321,
            appName = "测试图标包"
        )

        assertEquals("com.perficon.generated.longerpackname", ApkManifestEditor.readPackageName(patched))
        assertEquals(1_987_654_321, ApkManifestEditor.readVersionCode(patched))

    }
}
