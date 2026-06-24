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

        val originalPkg = ApkManifestEditor.readPackageName(originalManifest)
        println("ORIGINAL PACKAGE NAME: $originalPkg")

        val patched = ApkManifestEditor.patch(
            manifest = originalManifest,
            packageName = "com.perficon.generated.longerpackname",
            versionCode = 1_987_654_321,
            appName = "测试图标包"
        )

        assertEquals("com.perficon.generated.longerpackname", ApkManifestEditor.readPackageName(patched))
        assertEquals(1_987_654_321, ApkManifestEditor.readVersionCode(patched))

        val buffer = java.nio.ByteBuffer.wrap(patched).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val poolStart = buffer.getShort(2).toInt() and 0xffff
        val pool = ApkManifestEditor.StringPool.read(patched, poolStart)
        
        val hasOriginalActivityClass = pool.strings.any { it == "com.kian.perficontemplate.MainActivity" }
        val hasPatchedActivityClass = pool.strings.any { it == "com.perficon.generated.longerpackname.MainActivity" }
        val hasPatchedPermission = pool.strings.any { it == "com.perficon.generated.longerpackname.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" }
        
        org.junit.Assert.assertTrue("Should keep original MainActivity class name", hasOriginalActivityClass)
        org.junit.Assert.assertFalse("Should NOT patch MainActivity class name", hasPatchedActivityClass)
        org.junit.Assert.assertTrue("Should patch permission name prefix", hasPatchedPermission)
    }
}

