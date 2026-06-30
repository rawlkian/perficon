package com.kian.perficon.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class FastGeneratorScreenTest {
    @Test
    fun defaultsToSecondaryIconBackWhenPresent() {
        val paths = listOf(
            "/tmp/back_icon_back.png",
            "/tmp/back_icon_back_2.png"
        )

        assertEquals("/tmp/back_icon_back_2.png", paths.fastGeneratorDefaultBackPath())
    }

    @Test
    fun defaultsToSecondBackgroundWhenNamesAreGeneric() {
        val paths = listOf(
            "/tmp/background_one.png",
            "/tmp/background_two.png"
        )

        assertEquals("/tmp/background_two.png", paths.fastGeneratorDefaultBackPath())
    }
}
