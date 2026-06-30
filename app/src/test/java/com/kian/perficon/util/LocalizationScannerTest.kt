package com.kian.perficon.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class LocalizationScannerTest {

    @Test
    fun scanForLocalizationGaps() {
        val projectDir = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/java/com/kian/perficon/ui/AppLanguage.kt").exists() }
            ?: File(".").absoluteFile
        val srcDir = File(projectDir, "app/src/main/java/com/kian/perficon")
        val appLanguageFile = File(srcDir, "ui/AppLanguage.kt")

        assertTrue("Source directory not found: ${srcDir.absolutePath}", srcDir.exists())
        assertTrue("AppLanguage.kt not found: ${appLanguageFile.absolutePath}", appLanguageFile.exists())

        val appLangContent = appLanguageFile.readText()
        val mappedKeys = mutableSetOf<String>()
        val strPattern = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"")
        val strMatcher = strPattern.matcher(appLangContent)
        while (strMatcher.find()) {
            mappedKeys.add(strMatcher.group(1) ?: continue)
        }

        val chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]")
        val englishPattern = Pattern.compile("[A-Za-z]{2,}")
        val gaps = mutableListOf<String>()

        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                val lineNum = index + 1

                if (file.name == "AppLanguage.kt") return@forEachIndexed
                if (!line.contains("Text(") && !line.contains("localize(")) return@forEachIndexed

                val matcher = strPattern.matcher(line)
                while (matcher.find()) {
                    val literal = matcher.group(1)
                    if (literal.isNullOrBlank() || literal.contains("$") || isTechnicalLiteral(literal)) continue

                    val isVisibleChinese = chinesePattern.matcher(literal).find()
                    val isVisibleEnglish = englishPattern.matcher(literal).find() &&
                        !isVisibleChinese

                    if ((isVisibleChinese || isVisibleEnglish) && !isMappedOrDynamic(literal, mappedKeys)) {
                        gaps += "${file.relativeTo(projectDir)}:$lineNum \"$literal\""
                    }
                }
            }
        }

        assertTrue(
            "Missing localization entries:\n${gaps.joinToString("\n")}",
            gaps.isEmpty()
        )
    }

    private fun isMappedOrDynamic(literal: String, mappedKeys: Set<String>): Boolean {
        return mappedKeys.contains(literal) ||
            literal.startsWith("正在搜索 ") ||
            literal.startsWith("步骤 ") ||
            literal.startsWith("已生成 ") ||
            literal.endsWith(" 日") ||
            literal.endsWith(" 日图标") ||
            literal.startsWith("缩放：") ||
            literal.startsWith("错误：") ||
            literal.startsWith("已处理：") ||
            literal.startsWith("背景：") ||
            literal.startsWith("导入 ") ||
            literal.startsWith("确定要删除“") ||
            literal.endsWith(" 已保存到下载目录") ||
            literal.startsWith("Scale: ") ||
            literal.startsWith("Error: ") ||
            literal.startsWith("Processed: ") ||
            literal.startsWith("Backgrounds: ") ||
            literal.startsWith("Import ") ||
            (literal.startsWith("当前模板最多支持 ") && literal.endsWith(" 个动态日历图标。")) ||
            (literal.startsWith("当前模板最多支持 ") && literal.endsWith(" 个动态时钟图标。")) ||
            (literal.startsWith("模板只包含 ") && literal.endsWith(" 组完整的动态日历资源。")) ||
            (literal.startsWith("模板只包含 ") && literal.endsWith(" 组完整的动态时钟资源。")) ||
            (literal.startsWith("图标包模板最多支持 ") && literal.contains(" 个静态图标，当前项目有 "))
    }

    private fun isTechnicalLiteral(literal: String): Boolean {
        return literal == "Perficon" ||
            literal == "upon" ||
            literal == "back" ||
            literal == "mask" ||
            literal == "image/*" ||
            literal == "*/*" ||
            literal.startsWith("http") ||
            literal.contains("/")
    }
}
