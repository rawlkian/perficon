package com.kian.perficon.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackImporterTest {
    @Test
    fun normalizesDrawableReferencesFromXmlStyles() {
        assertEquals("meeyo_shadow", normalizeDrawableResourceName("@drawable/meeyo_shadow"))
        assertEquals("meeyo_shadow", normalizeDrawableResourceName("res/drawable-xhdpi-v4/meeyo_shadow.png"))
        assertEquals("meeyo_shadow", normalizeDrawableResourceName("com.example.pack:drawable/meeyo_shadow"))
    }

    @Test
    fun buildsAssetCandidatesForDensitySpecificOverlayFiles() {
        val candidates = drawableAssetCandidates("res/drawable-xhdpi-v4/meeyo_shadow.png")

        assertTrue(candidates.first() == "res/drawable-xhdpi-v4/meeyo_shadow.png")
        assertTrue(candidates.contains("res/drawable-xxxhdpi-v4/meeyo_shadow.png"))
        assertTrue(candidates.contains("res/drawable-xhdpi-v4/meeyo_shadow.webp"))
        assertTrue(candidates.contains("res/mipmap-xxxhdpi-v4/meeyo_shadow.png"))
    }
}
