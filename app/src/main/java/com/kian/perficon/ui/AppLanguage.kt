package com.kian.perficon.ui

import android.content.Context
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

enum class AppLanguage(val storageValue: String) {
    System("system"),
    Chinese("zh"),
    English("en");

    companion object {
        fun fromStorage(value: String?): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: System
    }
}

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("perficon_settings", Context.MODE_PRIVATE)
    val language = MutableStateFlow(AppLanguage.fromStorage(preferences.getString(LANGUAGE_KEY, null)))

    fun setLanguage(value: AppLanguage) {
        preferences.edit().putString(LANGUAGE_KEY, value.storageValue).apply()
        language.value = value
    }

    private companion object {
        const val LANGUAGE_KEY = "language"
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.System }

private fun AppLanguage.usesEnglish(): Boolean = when (this) {
    AppLanguage.English -> true
    AppLanguage.Chinese -> false
    AppLanguage.System -> Locale.getDefault().language.startsWith("en")
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = localize(text, LocalAppLanguage.current),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

fun localize(value: String, language: AppLanguage): String {
    val english = language.usesEnglish()
    if (english) {
        englishText[value]?.let { return it }
        return when {
            value.startsWith("正在搜索 ") -> "Searching ${localize(value.removePrefix("正在搜索 "), language)}"
            value.startsWith("步骤 ") -> "Step ${localize(value.removePrefix("步骤 "), language)}"
            value.startsWith("已生成 ") -> "Generated ${localize(value.removePrefix("已生成 "), language)}"
            value.endsWith(" 日") -> "Day ${value.removeSuffix(" 日")}" 
            value.endsWith(" 日图标") -> "Day ${value.removeSuffix(" 日图标")} icon"
            value.startsWith("缩放：") -> "Scale: ${localize(value.removePrefix("缩放："), language)}" 
            value.startsWith("错误：") -> "Error: ${localize(value.removePrefix("错误："), language)}" 
            value.startsWith("已处理：") -> "Processed: ${localize(value.removePrefix("已处理："), language)}" 
            value.startsWith("背景：") -> "Backgrounds: ${localize(value.removePrefix("背景："), language)}" 
            value.startsWith("导入 ") -> "Import ${localize(value.removePrefix("导入 "), language)}" 
            value.startsWith("确定要删除“") -> "Delete ${localize(value.removePrefix("确定要删除“").substringBefore("”"), language)}? This action cannot be undone."
            value.endsWith(" 已保存到下载目录") -> "${localize(value.removeSuffix(" 已保存到下载目录"), language)} saved to downloads directory"
            value.startsWith("当前模板最多支持 ") && value.endsWith(" 个动态日历图标。") -> {
                val num = value.removePrefix("当前模板最多支持 ").removeSuffix(" 个动态日历图标。")
                "The current template supports a maximum of $num dynamic calendar icons."
            }
            value.startsWith("当前模板最多支持 ") && value.endsWith(" 个动态时钟图标。") -> {
                val num = value.removePrefix("当前模板最多支持 ").removeSuffix(" 个动态时钟图标。")
                "The current template supports a maximum of $num dynamic clock icons."
            }
            value.startsWith("模板只包含 ") && value.endsWith(" 组完整的动态日历资源。") -> {
                val num = value.removePrefix("模板只包含 ").removeSuffix(" 组完整的动态日历资源。")
                "The template only contains $num complete dynamic calendar asset sets."
            }
            value.startsWith("模板只包含 ") && value.endsWith(" 组完整的动态时钟资源。") -> {
                val num = value.removePrefix("模板只包含 ").removeSuffix(" 组完整的动态时钟资源。")
                "The template only contains $num complete dynamic clock asset sets."
            }
            value.startsWith("图标包模板最多支持 ") && value.contains(" 个静态图标，当前项目有 ") && value.contains(" 个独特静态图标，请减少图标数量或使用支持更多槽位的模板。") -> {
                val parts = value.removePrefix("图标包模板最多支持 ").split(" 个静态图标，当前项目有 ")
                val slots = parts.firstOrNull() ?: ""
                val icons = parts.getOrNull(1)?.substringBefore(" 个独特静态图标") ?: ""
                "The template supports at most $slots static icons, but the project has $icons unique static icons. Please reduce icon count or use a template that supports more slots."
            }
            value.startsWith("构建 APK 失败：") -> "Failed to build APK: ${localize(value.removePrefix("构建 APK 失败："), language)}"
            else -> value
        }
    }
    chineseText[value]?.let { return it }
    return when {
        value.startsWith("Scale: ") -> "缩放：${value.removePrefix("Scale: ")}" 
        value.startsWith("Search") -> value.replaceFirst("Search", "搜索")
        value.startsWith("Error: ") -> "错误：${value.removePrefix("Error: ")}" 
        value.startsWith("Processed: ") -> "已处理：${value.removePrefix("Processed: ")}" 
        value.startsWith("Backgrounds: ") -> "背景：${value.removePrefix("Backgrounds: ")}" 
        value.startsWith("Import ") -> "导入 ${value.removePrefix("Import ")}" 
        value.startsWith("Restore default") -> "恢复默认${value.removePrefix("Restore default")}" 
        value.startsWith("Are you sure you want to delete ") -> "确定删除${value.removePrefix("Are you sure you want to delete ").removeSuffix("? This action cannot be undone.")}吗？此操作无法撤销。"
        value.endsWith(" saved to downloads directory") -> "${value.removeSuffix(" saved to downloads directory")} 已保存到下载目录"
        value.startsWith("This CandyBar template has ") && value.contains(" icon slots, but the project has ") -> {
            val parts = value.removePrefix("This CandyBar template has ").split(" icon slots, but the project has ")
            val slots = parts.firstOrNull() ?: ""
            val icons = parts.getOrNull(1)?.removeSuffix(" icons.") ?: ""
            "该 CandyBar 模板只有 $slots 个图标槽位，但项目有 $icons 个图标。"
        }
        else -> value
    }
}

private val englishText = mapOf(
    "需要存储访问权限" to "Storage permission required",
    "授权访问" to "Grant access",
    "新建项目" to "New project",
    "我的图标包" to "My icon packs",
    "创建图标包" to "Create icon pack",
    "从零创建" to "Create from scratch",
    "从空白项目开始" to "Start with a blank project",
    "导入已安装图标包" to "Import installed icon pack",
    "从设备中选择图标包" to "Choose an icon pack on this device",
    "编辑" to "Edit",
    "Activity" to "Activity",
    "编辑项目" to "Edit project",
    "保存" to "Save",
    "删除" to "Delete",
    "删除项目？" to "Delete project?",
    "取消" to "Cancel",
    "关闭" to "Close",
    "确认" to "Confirm",
    "复制" to "Duplicate",
    "开始制作第一个图标包" to "Create your first icon pack",
    "轻松设计并导出自定义图标。" to "Design and export custom icon packs.",
    "开始使用" to "Get started",
    "项目名称" to "Project name",
    "项目包名" to "Package name",
    "包名格式无效" to "Invalid package name",
    "必填" to "Required",
    "未选择项目图标" to "No project icon selected",
    "已选择项目图标" to "Project icon selected",
    "从图库选择" to "Choose from gallery",
    "从文件选择" to "Choose from files",
    "图标映射" to "Icon mappings",
    "动态" to "Dynamic",
    "样式" to "Style",
    "搜索结果" to "Search results",
    "搜索图标" to "Search icons",
    "图标名称" to "Icon name",
    "目标包名" to "Target package",
    "目标 Activity" to "Target activity",
    "包名" to "Package name",
    "手动填写" to "Enter manually",
    "未覆盖应用" to "Uncovered apps",
    "从已安装应用填入包名" to "Use an installed app",
    "图库" to "Gallery",
    "文件" to "Files",
    "快速生成" to "Quick generate",
    "更换图标" to "Replace icon",
    "编辑信息" to "Edit details",
    "设为项目图标" to "Set as project icon",
    "删除动态日历" to "Delete dynamic calendar",
    "删除动态时钟" to "Delete dynamic clock",
    "动态日历" to "Dynamic calendar",
    "动态时钟" to "Dynamic clock",
    "添加动态日历" to "Add dynamic calendar",
    "添加动态时钟" to "Add dynamic clock",
    "确认添加" to "Add",
    "恢复缺省图标" to "Restore default icon",
    "恢复缺省图层" to "Restore default layer",
    "从图库更换" to "Replace from gallery",
    "从文件更换" to "Replace from files",
    "表盘" to "Clock face",
    "时针" to "Hour hand",
    "分针" to "Minute hand",
    "秒针" to "Second hand",
    "正在构建 APK" to "Building APK",
    "正在创建动态日历" to "Creating dynamic calendar",
    "正在创建动态时钟" to "Creating dynamic clock",
    "正在生成 31 个日期图标" to "Generating 31 date icons",
    "还没有动态图标" to "No dynamic icons yet",
    "还没有图标映射" to "No icon mappings yet",
    "暂无此类图标" to "No icons in this group",
    "添加动态图标" to "Add dynamic icon",
    "添加图标" to "Add icon",
    "使用动态日历" to "Use dynamic calendar",
    "使用动态时钟" to "Use dynamic clock",
    "编辑动态素材" to "Edit dynamic assets",
    "该包名已经有图标映射" to "This package already has an icon mapping",
    "该包名已经有其他动态图标映射" to "This package already has another dynamic mapping",
    "按日期切换图标的应用" to "Apps that change their icon by date",
    "按当前时间旋转指针" to "Hands rotate with the current time",
    "点击右下角添加动态日历或动态时钟。" to "Use the add menu to create a dynamic calendar or clock.",
    "点击右下角菜单添加图标或快速生成。" to "Use the bottom-right menu to add an icon or generate one quickly.",
    "将创建 1 至 31 日的缺省图标，并按 CandyBar 默认日历应用规则导出。" to "This creates default icons for days 1 to 31 and exports with CandyBar calendar rules.",
    "将创建缺省表盘与指针图层，并按 CandyBar 默认时钟应用规则导出。" to "This creates default clock layers and exports with CandyBar clock rules.",
    "将根据当前项目设置生成并签名图标包 APK。" to "This builds and signs an icon-pack APK using the current project settings.",
    "将删除这组 1 至 31 日图标及其动态日历映射。" to "This deletes the 31 day icons and their dynamic calendar mapping.",
    "将删除表盘、指针图层及其动态时钟映射。" to "This deletes the clock face, hand layers, and dynamic clock mapping.",
    "请选择图片以生成日期图标" to "Choose an image to generate date icons",
    "例如：音乐" to "Example: Music",
    "统计信息" to "Statistics",
    "图标总数" to "Total icons",
    "已安装应用" to "Installed apps",
    "已覆盖" to "Covered",
    "未覆盖" to "Uncovered",
    "导入完成" to "Import complete",
    "已安装的图标包" to "Installed icon packs",
    "未找到图标包" to "No icon packs found",
    "添加背景" to "Add background",
    "背景颜色" to "Background color",
    "颜色" to "Color",
    "图片" to "Image",
    "原始图层" to "Original layer",
    "标准图标" to "Standard icon",
    "选择图标来源" to "Choose icon source",
    "两种模式只决定 Source Image 如何提取；" to "Both modes only change how the source image is extracted.",
    "点击预览以取色" to "Tap preview to pick a color",
    "使用取色器" to "Use color picker",
    "选择" to "Choose",
    "确定" to "OK",
    "正在导入图标..." to "Importing icons...",
    "像素字体" to "Pixel font",
    "确认导出" to "Confirm export",
    "开始导出" to "Export",
    "导出完成" to "Export complete",
    "安装" to "Install",
    "立即安装" to "Install now",
    "打开位置" to "Open location",
    "设置" to "Settings",
    "语言" to "Language",
    "跟随系统" to "Follow system",
    "中文" to "Chinese",
    "英文" to "English",
    "应用语言" to "App language",
    "外观与偏好" to "Appearance and preferences",
    "选择应用" to "Choose app",
    "生成图标预览" to "Generated icon preview",
    "保存" to "Save",
    "源图像" to "Source image",
    "相册" to "Gallery",
    "应用图标" to "App icon",
    "重置" to "Reset",
    "模板图层" to "Template layers",
    "背景" to "Background",
    "蒙版" to "Mask",
    "叠层" to "Overlay",
    "Perficon 需要“所有文件访问权限”来管理 /Perficon 中的图标包项目。" to "Perficon needs 'All Files Access' to manage your icon pack projects in the root folder (/Perficon).",
    "无法读取图片或生成日期图标，请换一张图片后重试。" to "Failed to read image or generate date icons, please try another image.",
    "图标蒙版" to "Icon Mask",
    "图标叠层" to "Icon Overlay",
    "搜索应用..." to "Search apps...",
    "恢复默认变换" to "Restore default transform",
    "恢复默认背景" to "Restore default background",
    "两种模式只决定 Source Image 如何提取；之后都会统一经过 Background、模板 Mask 和 Overlay。" to "Both modes only change how the source image is extracted; they will all go through Background, template Mask, and Overlay.",
    "无法保存日期图标" to "Failed to save date icon",
    "无法保存时钟图层" to "Failed to save clock layer",
    "APK 构建失败。" to "APK build failed.",
    "无法恢复缺省日期图标" to "Failed to restore default date icon",
    "无法恢复缺省时钟图层" to "Failed to restore default clock layer",
    "无法创建缺省动态时钟图标" to "Failed to create default dynamic clock icon",
    "无法创建缺省动态日历图标" to "Failed to create default dynamic calendar icon",
    "无法打开系统安装器" to "Failed to open system installer",
    "请授予 Perficon 安装应用的权限" to "Please grant Perficon permission to install apps",
    "无法打开下载目录" to "Failed to open downloads directory",
    "全部图标" to "All icons",
    "导入失败或部分完成" to "Import failed or partially completed",
    "正在准备导出" to "Preparing export",
    "正在加载图标包模板" to "Loading icon pack template",
    "正在写入图标与映射" to "Writing icons and mappings",
    "正在签名 APK" to "Signing APK",
    "正在完成导出" to "Completing export",
    "正在删除项目" to "Deleting project",
    "正在清理项目文件与数据库..." to "Cleaning up project files and database...",
    "关于" to "About",
    "返回" to "Back",
    "作者：Kian" to "Author: Kian",
    "恢复默认" to "Restore default",
    "更换" to "Replace",
    "欢迎使用 Perficon ！" to "Welcome to Perficon!",
    "您可以选择完全从新创建图标包或者导入已安装的图标包！现在就开始你的图标包创作之旅吧！" to "You can create an icon pack from scratch or import an installed icon pack! Start your icon pack creation journey now!",
    "更多" to "More",
    "轻松 design 并导出自定义图标。" to "Design and export custom icon packs easily.",
    "新建图标包" to "Create icon pack",
    "创建" to "Create",
    "说明 / 备注" to "Description",
    "说明 / 备注（可选）" to "Description (optional)",
    "正在启动构建" to "Starting build...",
    "无法将 APK 保存到下载目录。" to "Failed to save APK to downloads directory.",
    "编辑器" to "Editor",
    "导出" to "Export",
    "将创建缺省表盘与指针图层，并按图标包模板默认时钟应用规则导出。" to "This creates default clock face and hand layers, and exports with template clock rules.",
    "构建失败" to "Build failed",
    "从相册选择" to "Choose from gallery",
    "编辑动态日历" to "Edit dynamic calendar",
    "编辑动态时钟" to "Edit dynamic clock",
    "包含表盘与指针图层的图标" to "Icon containing clock face and hands",
    "应用于所有图标的裁切形状" to "Clipping shape applied to all icons",
    "显示在图标最上方的覆盖层" to "Overlay layer displayed on top of all icons",
    "背景图片" to "Background image",
    "图标背景" to "Icon background",
    "图标包模板（base.apk）缺失或无法读取。" to "Icon pack template (base.apk) is missing or unreadable.",
    "静态图标" to "Static icon",
    "动态日历" to "Dynamic calendar",
    "动态时钟" to "Dynamic clock",
    "未知文件" to "Unknown file",
    "以下项目图标文件不存在：\n" to "The following project icon files do not exist:\n",
    "导入被取消" to "Import cancelled",
    "构建 APK 失败：" to "Failed to build APK: "
)

private val chineseText = mapOf(
    "Project Name" to "项目名称",
    "Package Name" to "项目包名",
    "Import" to "导入",
    "Importing Icons..." to "正在导入图标...",
    "Import complete" to "导入完成",
    "Processed:" to "已处理：",
    "Icon Mask" to "图标蒙版",
    "Backgrounds:" to "背景：",
    "My icon packs" to "我的图标包",
    "New project" to "新建项目",
    "Create icon pack" to "创建图标包",
    "Create from scratch" to "从零创建",
    "Start with a blank project" to "从空白项目开始",
    "Import installed icon pack" to "导入已安装图标包",
    "Choose an icon pack on this device" to "从设备中选择图标包",
    "No project icon selected" to "未选择项目图标",
    "Project icon selected" to "已选择项目图标",
    "Choose from gallery" to "从图库选择",
    "Choose from files" to "从文件选择",
    "Invalid package name" to "包名格式无效",
    "Required" to "必填",
    "Delete project?" to "删除项目？",
    "Pixel font" to "像素字体",
    "Fusion Pixel 10px" to "Fusion Pixel 10px 像素字体",
    "Perficon needs 'All Files Access' to manage your icon pack projects in the root folder (/Perficon)." to "Perficon 需要“所有文件访问权限”来管理 /Perficon 中的图标包项目。",
    "1. Overlay" to "1. 叠层",
    "2. Mask (Shape)" to "2. 蒙版（形状）",
    "3. Background" to "3. 背景"
)
