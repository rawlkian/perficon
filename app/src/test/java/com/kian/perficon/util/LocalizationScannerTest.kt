package com.kian.perficon.util

import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class LocalizationScannerTest {

    @Test
    fun scanForLocalizationGaps() {
        val projectDir = File("d:/AndroidProject/Perficon")
        val srcDir = File(projectDir, "app/src/main/java/com/kian/perficon")
        val appLanguageFile = File(srcDir, "ui/AppLanguage.kt")

        if (!srcDir.exists() || !appLanguageFile.exists()) {
            println("Source directory or AppLanguage.kt not found.")
            return
        }

        // 1. Parse AppLanguage.kt to load all mapped keys
        val appLangContent = appLanguageFile.readText()
        val mappedKeys = mutableSetOf<String>()
        
        // Extract all string literals "..." in the file directly
        val strPattern = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"")
        val strMatcher = strPattern.matcher(appLangContent)
        while (strMatcher.find()) {
            mappedKeys.add(strMatcher.group(1))
        }
        
        println("Found ${mappedKeys.size} mapped string keys in AppLanguage.kt")

        val chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]")
        var gapsCount = 0

        // 2. Scan all .kt files in srcDir
        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                val lineNum = index + 1
                
                // Match all string literals in the line
                val strPattern = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"")
                val strMatcher = strPattern.matcher(line)
                while (strMatcher.find()) {
                    val literal = strMatcher.group(1)
                    if (chinesePattern.matcher(literal).find()) {
                        // Skip if we are analyzing AppLanguage.kt itself (inside mapOf definitions)
                        if (file.name == "AppLanguage.kt" && (line.contains("to") || line.contains("mapOf"))) {
                            continue
                        }
                        
                        // Check if the literal is in the translation map or handled by custom rule
                        val isMapped = mappedKeys.contains(literal) || 
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
                                       (literal.startsWith("当前模板最多支持 ") && literal.endsWith(" 个动态日历图标。")) ||
                                       (literal.startsWith("当前模板最多支持 ") && literal.endsWith(" 个动态时钟图标。")) ||
                                       (literal.startsWith("模板只包含 ") && literal.endsWith(" 组完整的动态日历资源。")) ||
                                       (literal.startsWith("模板只包含 ") && literal.endsWith(" 组完整的动态时钟资源。")) ||
                                       (literal.startsWith("图标包模板最多支持 ") && literal.contains(" 个静态图标，当前项目有 ")) ||
                                       (literal.startsWith("第 ") && literal.endsWith(" 天"))

                        if (!isMapped) {
                            println("[GAP] ${file.name}:$lineNum - Chinese string is not in AppLanguage dictionary: \"$literal\"")
                            gapsCount++
                        }
                        
                        // Also inspect if it is used outside Text() or localize()
                        // This helps catch strings directly passed to native APIs like Toast, Log, or non-localized components.
                        val isWrapped = line.contains("Text(") || line.contains("localize(") || file.name == "AppLanguage.kt"
                        if (!isWrapped) {
                            println("[UNWRAPPED] ${file.name}:$lineNum - Chinese literal used without localization wrapper: $line")
                        }
                    }
                }
            }
        }

        println("Localization scan completed. Found $gapsCount unmapped Chinese literals.")
    }
}
