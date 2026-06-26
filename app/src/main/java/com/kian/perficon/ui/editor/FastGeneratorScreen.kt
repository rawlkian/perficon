package com.kian.perficon.ui.editor

import com.kian.perficon.ui.localize
import com.kian.perficon.ui.LocalAppLanguage
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kian.perficon.model.IconPackProject
import com.kian.perficon.ui.AppPicker
import com.kian.perficon.ui.Text
import com.kian.perficon.ui.components.*
import com.kian.perficon.util.StorageHelper
import com.kian.perficon.util.getInstalledApps
import com.kian.perficon.util.saveIconToInternalStorage
import androidx.compose.foundation.BorderStroke
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FastGeneratorScreen(
    iconName: String,
    targetPackageName: String,
    project: IconPackProject,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenTitle = iconName.ifBlank { targetPackageName.substringAfterLast(".").ifBlank { "快速生成" } }

    // 当前Fast Gen会话中的临时参数。
    var scale by remember { mutableFloatStateOf(project.scaleFactor) }
    var maskPath by remember { mutableStateOf(project.iconMaskPath) }
    var uponPath by remember { mutableStateOf(project.iconUponPath) }
    var backPath by remember {
        mutableStateOf(project.iconBackPaths?.split(",")?.firstOrNull())
    }
    var backgroundColor by remember { mutableStateOf<Color?>(null) }

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appIconToProcess by remember { mutableStateOf<Drawable?>(null) }
    var showIconTypeChoice by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var isEyedropperActive by remember { mutableStateOf(false) }

    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var uponBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(maskPath) {
        val path = maskPath
        maskBitmap = if (path != null) {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(path)
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            null
        }
    }
    DisposableEffect(maskBitmap) {
        val bitmap = maskBitmap
        onDispose {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    LaunchedEffect(backPath) {
        val path = backPath
        backBitmap = if (path != null) {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(path)
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            null
        }
    }
    DisposableEffect(backBitmap) {
        val bitmap = backBitmap
        onDispose {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    LaunchedEffect(uponPath) {
        val path = uponPath
        uponBitmap = if (path != null) {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(path)
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            null
        }
    }
    DisposableEffect(uponBitmap) {
        val bitmap = uponBitmap
        onDispose {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /*
     * 无论 Source Image 来自Gallery、原始图层还是标准图标，
     * 最终都只通过这个入口替换 sourceBitmap。
     *
     * 这保证“来源类型”只影响素材提取，不影响后续的：
     * Background → Source → Mask → Overlay 处理逻辑。
     */
    fun replaceSourceBitmap(newBitmap: Bitmap) {
        sourceBitmap
            ?.takeIf { oldBitmap ->
                oldBitmap !== newBitmap &&
                        !oldBitmap.isRecycled
            }
            ?.recycle()

        sourceBitmap = newBitmap
        offset = Offset.Zero
    }

    LaunchedEffect(targetPackageName) {
        if (targetPackageName.isBlank() || sourceBitmap != null) return@LaunchedEffect
        val drawable = withContext(Dispatchers.IO) {
            getInstalledApps(context).firstOrNull { it.packageName == targetPackageName }?.icon
        }
        drawable?.let {
            replaceSourceBitmap(drawableToOriginalBitmap(it, 512))
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)?.let { decodedBitmap ->
                replaceSourceBitmap(decodedBitmap)
            }
        }
    }

    var pickingAssetType by remember { mutableStateOf<String?>(null) }

    val universalPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult

        saveIconToInternalStorage(
            context = context,
            uri = uri,
            fileName = "gen_asset_${System.currentTimeMillis()}.png",
            projectId = project.id
        )?.let { path ->
            when (pickingAssetType) {
                "mask" -> {
                    maskPath = path
                }

                "back" -> {
                    backPath = path
                    backgroundColor = null
                }

                "upon" -> {
                    uponPath = path
                }
            }
        }

        pickingAssetType = null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(screenTitle)
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = localize("取消", LocalAppLanguage.current)
                        )
                    }
                },
                actions = {
                    RetroButton(
                        onClick = {
                            val generated = generateFinalIcon(
                                source = sourceBitmap,
                                maskBitmap = maskBitmap,
                                backBitmap = backBitmap,
                                backgroundColor = backgroundColor,
                                uponBitmap = uponBitmap,
                                scale = scale,
                                offset = offset,
                                density = density.density
                            )

                            val outputFile = File(
                                StorageHelper.getProjectIconsDir(project.id),
                                "gen_${System.currentTimeMillis()}.png"
                            )

                            FileOutputStream(outputFile).use { outputStream ->
                                generated.compress(
                                    Bitmap.CompressFormat.PNG,
                                    100,
                                    outputStream
                                )
                            }

                            generated.recycle()
                            onSave(outputFile.absolutePath)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "保存",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 预览区域。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                /*
                 * 预览和Save共用 generateFinalIcon()，
                 * 因此不会出现预览与最终Build结果不一致的问题。
                 */
                val previewBitmap = remember(
                    sourceBitmap,
                    maskBitmap,
                    backBitmap,
                    backgroundColor,
                    uponBitmap,
                    scale,
                    offset,
                    density.density
                ) {
                    generateFinalIcon(
                        source = sourceBitmap,
                        maskBitmap = maskBitmap,
                        backBitmap = backBitmap,
                        backgroundColor = backgroundColor,
                        uponBitmap = uponBitmap,
                        scale = scale,
                        offset = offset,
                        density = density.density
                    )
                }

                DisposableEffect(previewBitmap) {
                    onDispose {
                        if (!previewBitmap.isRecycled) {
                            previewBitmap.recycle()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(192.dp)
                        .background(Color.Transparent)
                        .pointerInput(isEyedropperActive) {
                            if (isEyedropperActive) {
                                detectTapGestures { tapOffset ->
                                    val previewSizePx = 192.dp.toPx()

                                    val x = (
                                            tapOffset.x *
                                                    previewBitmap.width /
                                                    previewSizePx
                                            )
                                        .toInt()
                                        .coerceIn(
                                            0,
                                            previewBitmap.width - 1
                                        )

                                    val y = (
                                            tapOffset.y *
                                                    previewBitmap.height /
                                                    previewSizePx
                                            )
                                        .toInt()
                                        .coerceIn(
                                            0,
                                            previewBitmap.height - 1
                                        )

                                    backgroundColor = Color(
                                        previewBitmap.getPixel(x, y)
                                    )
                                    backPath = null
                                    isEyedropperActive = false
                                }
                            } else {
                                detectTransformGestures {
                                        _,
                                        pan,
                                        zoom,
                                        _ ->

                                    scale = (scale * zoom)
                                        .coerceIn(0.25f, 2.0f)

                                    offset += pan
                                }
                            }
                        }
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = localize("生成图标预览", LocalAppLanguage.current),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isEyedropperActive) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "点击预览以取色",
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 缩放控制。
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scale: ${(scale * 100).toInt()}%",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge
                        )

                        IconButton(
                            onClick = {
                                scale = project.scaleFactor
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.SettingsBackupRestore,
                                contentDescription = localize("恢复默认变换", LocalAppLanguage.current)
                            )
                        }
                    }

                    Slider(
                        value = scale,
                        onValueChange = {
                            scale = it
                        },
                        valueRange = 0.25f..2.0f
                    )
                }

                // 待处理图标Select与位置复位。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "源图像",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedButton(
                        onClick = {
                            offset = Offset.Zero
                        },
                        enabled = offset != Offset.Zero,
                        contentPadding = PaddingValues(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsBackupRestore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("重置")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RetroButton(
                        onClick = {
                            imageLauncher.launch("image/*")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("相册")
                    }

                    RetroOutlinedButton(
                        onClick = {
                            showAppPicker = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("应用图标")
                    }
                }

                // Template Layers设置。
                Text(
                    text = "模板图层",
                    style = MaterialTheme.typography.titleMedium
                )

                AssetRow(
                    label = "1. Overlay",
                    path = uponPath,
                    onPick = {
                        pickingAssetType = "upon"
                        universalPicker.launch("image/*")
                    },
                    onRestore = {
                        uponPath = project.iconUponPath
                    }
                )

                AssetRow(
                    label = "2. Mask (Shape)",
                    path = maskPath,
                    onPick = {
                        pickingAssetType = "mask"
                        universalPicker.launch("image/*")
                    },
                    onRestore = {
                        maskPath = project.iconMaskPath
                    }
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. Background",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        IconButton(
                            onClick = {
                                backPath = project.iconBackPaths
                                    ?.split(",")
                                    ?.firstOrNull()

                                backgroundColor = null
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.SettingsBackupRestore,
                                contentDescription = localize("恢复默认背景", LocalAppLanguage.current)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                pickingAssetType = "back"
                                universalPicker.launch("image/*")
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = if (backPath != null) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                horizontalArrangement =
                                    Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (backPath != null) {
                                        Icons.Default.Image
                                    } else {
                                        Icons.Default.FileUpload
                                    },
                                    contentDescription = null
                                )

                                Spacer(Modifier.width(8.dp))
                                Text("图片")
                            }
                        }

                        Surface(
                            onClick = {
                                showColorPicker = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = if (backgroundColor != null) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                horizontalArrangement =
                                    Arrangement.Center
                            ) {
                                if (backgroundColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                backgroundColor!!,
                                                CircleShape
                                            )
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null
                                    )
                                }

                                Spacer(Modifier.width(8.dp))
                                Text("颜色")
                            }
                        }
                    }
                }
            }
        }

        if (showAppPicker) {
            AppPicker(
                onDismiss = {
                    showAppPicker = false
                },
                onAppSelected = {
                    appIconToProcess = it.icon
                    showIconTypeChoice = true
                    showAppPicker = false
                }
            )
        }

        if (
            showIconTypeChoice &&
            appIconToProcess != null
        ) {
            RetroDialog(
                onDismissRequest = {
                    showIconTypeChoice = false
                    appIconToProcess = null
                }
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("选择图标来源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "两种模式只决定 Source Image 如何提取；之后都会统一经过 Background、模板 Mask 和 Overlay。"
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RetroOutlinedButton(
                            onClick = {
                                val drawable = requireNotNull(appIconToProcess)
                                replaceSourceBitmap(
                                    drawableToStandardBitmap(
                                        drawable = drawable,
                                        size = 512
                                    )
                                )
                                showIconTypeChoice = false
                                appIconToProcess = null
                            }
                        ) {
                            Text("标准图标")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroButton(
                            onClick = {
                                val drawable = requireNotNull(appIconToProcess)
                                replaceSourceBitmap(
                                    drawableToOriginalBitmap(
                                        drawable = drawable,
                                        size = 512
                                    )
                                )
                                showIconTypeChoice = false
                                appIconToProcess = null
                            }
                        ) {
                            Text("原始图层")
                        }
                    }
                }
            }
        }

        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = backgroundColor,
                onDismiss = {
                    showColorPicker = false
                },
                onEyedropper = {
                    showColorPicker = false
                    isEyedropperActive = true
                },
                onColorSelected = {
                    backgroundColor = it
                    backPath = null
                    showColorPicker = false
                }
            )
        }
    }
}

@Composable
fun AssetRow(
    label: String,
    path: String?,
    onPick: () -> Unit,
    onRestore: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (path != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(40.dp)
                ) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "$label preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            IconButton(
                onClick = onRestore
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsBackupRestore,
                    contentDescription = localize("恢复默认", LocalAppLanguage.current),
                    modifier = Modifier.size(20.dp)
                )
            }

            RetroOutlinedButton(
                onClick = onPick,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = if (path == null) {
                        "选择"
                    } else {
                        "更换"
                    }
                )
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Color?,
    onDismiss: () -> Unit,
    onEyedropper: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    fun toHex(red: Int, green: Int, blue: Int): String = "#%02X%02X%02X".format(red, green, blue)
    fun parseHex(value: String): Color? {
        val raw = value.trim().removePrefix("#")
        if (raw.length != 6 || raw.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val intValue = raw.toInt(16)
        return Color(
            red = (intValue shr 16 and 0xff) / 255f,
            green = (intValue shr 8 and 0xff) / 255f,
            blue = (intValue and 0xff) / 255f,
            alpha = 1f
        )
    }

    var red by remember(initialColor) { mutableFloatStateOf((initialColor?.let { channel(it.red) } ?: 255).toFloat()) }
    var green by remember(initialColor) { mutableFloatStateOf((initialColor?.let { channel(it.green) } ?: 255).toFloat()) }
    var blue by remember(initialColor) { mutableFloatStateOf((initialColor?.let { channel(it.blue) } ?: 255).toFloat()) }
    var hexInput by remember(initialColor) {
        mutableStateOf(toHex(red.roundToInt(), green.roundToInt(), blue.roundToInt()))
    }
    val selectedColor = Color(
        red = red.roundToInt() / 255f,
        green = green.roundToInt() / 255f,
        blue = blue.roundToInt() / 255f,
        alpha = 1f
    )

    val commonColors = listOf(
        Color.Red,
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color.Yellow,
        Color.Cyan,
        Color.Magenta,
        Color.Black,
        Color.White,
        Color.Gray
    )

    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("背景颜色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                commonColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color, CircleShape)
                            .clickable {
                                onColorSelected(color)
                            }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(selectedColor, MaterialTheme.shapes.medium)
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { value ->
                        hexInput = value
                        parseHex(value)?.let { parsed ->
                            red = channel(parsed.red).toFloat()
                            green = channel(parsed.green).toFloat()
                            blue = channel(parsed.blue).toFloat()
                        }
                    },
                    label = { Text("HEX") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            ColorChannelSlider("R", red, Color.Red) {
                red = it
                hexInput = toHex(red.roundToInt(), green.roundToInt(), blue.roundToInt())
            }
            ColorChannelSlider("G", green, Color(0xFF4CAF50)) {
                green = it
                hexInput = toHex(red.roundToInt(), green.roundToInt(), blue.roundToInt())
            }
            ColorChannelSlider("B", blue, Color(0xFF2196F3)) {
                blue = it
                hexInput = toHex(red.roundToInt(), green.roundToInt(), blue.roundToInt())
            }

            Spacer(Modifier.height(16.dp))

            RetroOutlinedButton(
                onClick = onEyedropper,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Colorize,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("使用取色器")
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(Modifier.width(8.dp))
                RetroButton(onClick = { onColorSelected(selectedColor) }) {
                    Text("确认")
                }
            }
        }
    }
}

@Composable
private fun ColorChannelSlider(label: String, value: Float, color: Color, onValueChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold, color = color)
            Text(value.roundToInt().toString(), style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..255f)
    }
}

/**
 * 将 Drawable 绘制到固定大小的位图，同时恢复原 bounds。
 */
private fun drawDrawableToBitmap(
    drawable: Drawable,
    size: Int
): Bitmap {
    val safeSize = size.coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(
        safeSize,
        safeSize,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)
    val originalBounds = Rect(drawable.bounds)

    drawable.setBounds(
        0,
        0,
        safeSize,
        safeSize
    )
    drawable.draw(canvas)
    drawable.bounds = originalBounds

    return bitmap
}

/**
 * 标准图标：
 *
 * 直接绘制完整 Drawable。
 * 如果它是 AdaptiveIconDrawable，这一步会保留 Android / 厂商
 * 当前提供的标准显示效果。
 *
 * 注意：
 * 这里只负责提取 Source Image。
 * 后续仍会继续经过项目的 Background、Mask 和 Overlay。
 */
fun drawableToStandardBitmap(
    drawable: Drawable,
    size: Int = 512
): Bitmap {
    return drawDrawableToBitmap(
        drawable = drawable,
        size = size
    )
}

/**
 * 原始图层：
 *
 * 对 AdaptiveIconDrawable 分别绘制 background 与 foreground，
 * 不直接调用 AdaptiveIconDrawable.draw()，从而避免在素材提取阶段
 * 提前固定为系统图标形状。
 *
 * 这里只改变 Source Image 的提取方式。
 * 提取完成后，它与“标准图标”完全共用同一个 generateFinalIcon()。
 */
fun drawableToOriginalBitmap(
    drawable: Drawable,
    size: Int = 512
): Bitmap {
    val safeSize = size.coerceAtLeast(1)

    if (
        android.os.Build.VERSION.SDK_INT >=
        android.os.Build.VERSION_CODES.O &&
        drawable is android.graphics.drawable.AdaptiveIconDrawable
    ) {
        val bitmap = Bitmap.createBitmap(
            safeSize,
            safeSize,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        drawable.background?.let { background ->
            val originalBounds =
                Rect(background.bounds)

            background.setBounds(
                0,
                0,
                safeSize,
                safeSize
            )
            background.draw(canvas)
            background.bounds = originalBounds
        }

        drawable.foreground?.let { foreground ->
            val originalBounds =
                Rect(foreground.bounds)

            foreground.setBounds(
                0,
                0,
                safeSize,
                safeSize
            )
            foreground.draw(canvas)
            foreground.bounds = originalBounds
        }

        return bitmap
    }

    /*
     * 普通 BitmapDrawable / VectorDrawable 不存在可拆分的
     * Adaptive foreground/background，直接按标准方式读取即可。
     */
    return drawDrawableToBitmap(
        drawable = drawable,
        size = safeSize
    )
}

/**
 * 计算 RGB 像素的亮度。
 *
 * 有些图标包的 Mask 是真正的 Alpha 遮罩；
 * 另一些则是完全不透明的黑白Image。
 * 因此不能只读取 Alpha，也不能只读取黑白Color。
 */
private fun pixelLuminance(
    pixel: Int
): Int {
    val red = android.graphics.Color.red(pixel)
    val green = android.graphics.Color.green(pixel)
    val blue = android.graphics.Color.blue(pixel)

    return (
            red * 0.2126f +
                    green * 0.7152f +
                    blue * 0.0722f
            )
        .toInt()
        .coerceIn(0, 255)
}

/**
 * 从覆盖率数组中读取若干归一化坐标的平均值。
 */
private fun averageCoverage(
    coverage: IntArray,
    width: Int,
    height: Int,
    normalizedPoints: List<Pair<Float, Float>>
): Float {
    if (
        width <= 0 ||
        height <= 0 ||
        coverage.isEmpty() ||
        normalizedPoints.isEmpty()
    ) {
        return 0f
    }

    var sum = 0f

    normalizedPoints.forEach { (normalizedX, normalizedY) ->
        val x = (
                normalizedX.coerceIn(0f, 1f) *
                        (width - 1)
                )
            .toInt()
            .coerceIn(0, width - 1)

        val y = (
                normalizedY.coerceIn(0f, 1f) *
                        (height - 1)
                )
            .toInt()
            .coerceIn(0, height - 1)

        sum += coverage[y * width + x]
    }

    return sum / normalizedPoints.size
}

/**
 * 将任意 Mask 统一转换为标准形状 Alpha 遮罩：
 *
 * - 形状内部：Alpha = 255
 * - 形状外部：Alpha = 0
 *
 * 支持两类常见资源：
 *
 * 1. Alpha Mask
 *    中央或外围通过透明度区分。
 *
 * 2. 黑白 Mask
 *    Image整体可能完全不透明，依靠黑白亮度区分。
 *
 * 转换完成后，后续合成不再猜测 DST_IN / DST_OUT，
 * 而是始终使用 DST_IN。
 */
private fun createNormalizedShapeMask(
    sourceMask: Bitmap,
    outputSize: Int
): Bitmap {
    val safeSize = outputSize.coerceAtLeast(1)

    /*
     * 先将原始 Mask 平滑缩放到最终输出尺寸，
     * 保证背景、Source Image 与 Mask 使用完全相同的坐标。
     */
    val scaledMask = Bitmap.createBitmap(
        safeSize,
        safeSize,
        Bitmap.Config.ARGB_8888
    )

    val scaledCanvas = Canvas(scaledMask)
    val scalePaint = Paint(
        Paint.ANTI_ALIAS_FLAG or
                Paint.FILTER_BITMAP_FLAG
    )

    scaledCanvas.drawBitmap(
        sourceMask,
        null,
        Rect(
            0,
            0,
            safeSize,
            safeSize
        ),
        scalePaint
    )

    val pixels = IntArray(safeSize * safeSize)

    scaledMask.getPixels(
        pixels,
        0,
        safeSize,
        0,
        0,
        safeSize,
        safeSize
    )

    var minAlpha = 255
    var maxAlpha = 0
    var minLuminance = 255
    var maxLuminance = 0

    /*
     * 每隔少量像素采样即可判断该 Mask 主要依赖
     * Alpha 还是黑白亮度，不需要为判断阶段遍历全部像素。
     */
    val sampleStep = (safeSize / 64).coerceAtLeast(1)

    var sampleY = 0
    while (sampleY < safeSize) {
        var sampleX = 0

        while (sampleX < safeSize) {
            val pixel = pixels[
                sampleY * safeSize + sampleX
            ]

            val alpha =
                android.graphics.Color.alpha(pixel)

            val luminance = pixelLuminance(pixel)

            minAlpha = minOf(minAlpha, alpha)
            maxAlpha = maxOf(maxAlpha, alpha)
            minLuminance =
                minOf(minLuminance, luminance)
            maxLuminance =
                maxOf(maxLuminance, luminance)

            sampleX += sampleStep
        }

        sampleY += sampleStep
    }

    val alphaRange = maxAlpha - minAlpha
    val luminanceRange =
        maxLuminance - minLuminance

    /*
     * Alpha 有明显变化时，优先把它视为 Alpha Mask。
     * 如果 Alpha 几乎恒定但黑白差异明显，则使用亮度。
     */
    val useAlphaChannel =
        alphaRange >= 24 ||
                luminanceRange < 24

    val rawCoverage =
        IntArray(safeSize * safeSize)

    for (index in pixels.indices) {
        val pixel = pixels[index]
        val alpha =
            android.graphics.Color.alpha(pixel)

        rawCoverage[index] =
            if (useAlphaChannel) {
                alpha
            } else {
                /*
                 * 黑白 Mask 仍可能在边缘包含半透明抗锯齿；
                 * 将亮度和 Alpha 相乘可保留平滑边缘。
                 */
                (
                        pixelLuminance(pixel) *
                                alpha / 255f
                        )
                    .toInt()
                    .coerceIn(0, 255)
            }
    }

    val centerPoints = listOf(
        0.50f to 0.50f,
        0.42f to 0.50f,
        0.58f to 0.50f,
        0.50f to 0.42f,
        0.50f to 0.58f,
        0.42f to 0.42f,
        0.58f to 0.42f,
        0.42f to 0.58f,
        0.58f to 0.58f
    )

    val outerPoints = listOf(
        0.02f to 0.02f,
        0.50f to 0.02f,
        0.98f to 0.02f,
        0.02f to 0.50f,
        0.98f to 0.50f,
        0.02f to 0.98f,
        0.50f to 0.98f,
        0.98f to 0.98f
    )

    val centerCoverage = averageCoverage(
        coverage = rawCoverage,
        width = safeSize,
        height = safeSize,
        normalizedPoints = centerPoints
    )

    val outerCoverage = averageCoverage(
        coverage = rawCoverage,
        width = safeSize,
        height = safeSize,
        normalizedPoints = outerPoints
    )

    /*
     * 标准化方向：
     *
     * 中央值较高：原 Mask 已经是“内部为白/不透明”。
     * 外围值较高：原 Mask 是传统反向 Mask，需要反相。
     *
     * 两者非常接近时保持原方向，避免对纯色或异常资源
     * 进行毫无根据的反相。
     */
    val shouldInvert =
        outerCoverage > centerCoverage + 8f

    val normalizedPixels =
        IntArray(safeSize * safeSize)

    for (index in rawCoverage.indices) {
        val normalizedAlpha =
            if (shouldInvert) {
                255 - rawCoverage[index]
            } else {
                rawCoverage[index]
            }
                .coerceIn(0, 255)

        normalizedPixels[index] =
            android.graphics.Color.argb(
                normalizedAlpha,
                255,
                255,
                255
            )
    }

    val normalizedMask = Bitmap.createBitmap(
        safeSize,
        safeSize,
        Bitmap.Config.ARGB_8888
    )

    normalizedMask.setPixels(
        normalizedPixels,
        0,
        safeSize,
        0,
        0,
        safeSize,
        safeSize
    )

    if (!scaledMask.isRecycled) {
        scaledMask.recycle()
    }

    return normalizedMask
}

/**
 * 使用已经标准化的形状遮罩裁切指定图层。
 *
 * shapeMask 的形状内部永远不透明、外部永远透明，
 * 所以这里始终使用 DST_IN：
 *
 * - 当前 layerBitmap 是 DST；
 * - shapeMask 是 SRC；
 * - 只保留 SRC Alpha 覆盖的 DST 像素。
 */
private fun applyNormalizedMask(
    layerBitmap: Bitmap,
    shapeMask: Bitmap
) {
    val layerCanvas = Canvas(layerBitmap)

    val maskPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or
                Paint.FILTER_BITMAP_FLAG
    ).apply {
        xfermode = PorterDuffXfermode(
            PorterDuff.Mode.DST_IN
        )
    }

    layerCanvas.drawBitmap(
        shapeMask,
        0f,
        0f,
        maskPaint
    )

    maskPaint.xfermode = null
}

/**
 * 生成最终图标。
 *
 * 无论 Source Image 是：
 * - GalleryImage；
 * - 原始 Adaptive 图层；
 * - 系统标准图标；
 *
 * 都严格执行同一条处理流程：
 *
 * 1. 在 contentLayer 上绘制 Background；
 * 2. 在 Background 上方绘制 Source Image；
 * 3. 对整个 contentLayer 应用模板 Mask；
 * 4. 将裁切结果绘制到最终画布；
 * 5. 最后绘制 Overlay。
 *
 * 因此“原始图层”和“标准图标”的区别只存在于素材提取阶段，
 * 不会跳过 Mask、Background 或 Overlay。
 */
fun generateFinalIcon(
    source: Bitmap?,
    maskBitmap: Bitmap?,
    backBitmap: Bitmap?,
    backgroundColor: Color?,
    uponBitmap: Bitmap?,
    scale: Float,
    offset: Offset,
    density: Float
): Bitmap {
    val outputSize = 512

    val outputRect = Rect(
        0,
        0,
        outputSize,
        outputSize
    )

    val normalPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or
                Paint.FILTER_BITMAP_FLAG
    )

    val resultBitmap = Bitmap.createBitmap(
        outputSize,
        outputSize,
        Bitmap.Config.ARGB_8888
    )
    val resultCanvas = Canvas(resultBitmap)

    /*
     * Background 与 Source Image 先合成到同一张透明内容层。
     * 模板 Mask 随后只执行一次，避免不同来源走出不同逻辑。
     */
    val contentLayer = Bitmap.createBitmap(
        outputSize,
        outputSize,
        Bitmap.Config.ARGB_8888
    )
    val contentCanvas = Canvas(contentLayer)

    // ====================================================
    // Layer 1：Background
    // ====================================================
    when {
        backgroundColor != null -> {
            contentCanvas.drawColor(
                backgroundColor.toArgb()
            )
        }

        backBitmap != null -> {
            contentCanvas.drawBitmap(
                backBitmap,
                null,
                outputRect,
                normalPaint
            )
        }
    }

    // ====================================================
    // Layer 2：Source Image
    // ====================================================
    if (
        source != null &&
        !source.isRecycled &&
        source.width > 0 &&
        source.height > 0
    ) {
        contentCanvas.save()

        /*
         * offset 来自 192.dp 预览区域中的手势像素，
         * 在这里换算为 512 × 512 输出坐标。
         */
        val previewSizePx =
            (192f * density).coerceAtLeast(1f)

        val previewToOutputScale =
            outputSize.toFloat() /
                    previewSizePx

        contentCanvas.translate(
            outputSize / 2f +
                    offset.x * previewToOutputScale,
            outputSize / 2f +
                    offset.y * previewToOutputScale
        )

        contentCanvas.scale(
            scale,
            scale
        )

        contentCanvas.translate(
            -outputSize / 2f,
            -outputSize / 2f
        )

        val sourceAspectRatio =
            source.width.toFloat() /
                    source.height.toFloat()

        val drawWidth: Float
        val drawHeight: Float

        if (sourceAspectRatio >= 1f) {
            drawWidth = outputSize.toFloat()
            drawHeight =
                outputSize / sourceAspectRatio
        } else {
            drawWidth =
                outputSize * sourceAspectRatio
            drawHeight = outputSize.toFloat()
        }

        val destinationLeft =
            (outputSize - drawWidth) / 2f

        val destinationTop =
            (outputSize - drawHeight) / 2f

        val destinationRect = RectF(
            destinationLeft,
            destinationTop,
            destinationLeft + drawWidth,
            destinationTop + drawHeight
        )

        contentCanvas.drawBitmap(
            source,
            null,
            destinationRect,
            normalPaint
        )

        contentCanvas.restore()
    }

    // ====================================================
    // Layer 3：Template Mask
    //
    // 对 Background + Source Image 的整体结果只裁切一次。
    // ====================================================
    val shapeMask = maskBitmap
        ?.let { mask ->
            createNormalizedShapeMask(
                sourceMask = mask,
                outputSize = outputSize
            )
        }

    if (shapeMask != null) {
        applyNormalizedMask(
            layerBitmap = contentLayer,
            shapeMask = shapeMask
        )
    }

    /*
     * Mask 完成后才把整个内容层绘制到最终画布。
     */
    resultCanvas.drawBitmap(
        contentLayer,
        0f,
        0f,
        normalPaint
    )

    // ====================================================
    // Layer 4：Overlay
    // ====================================================
    if (uponBitmap != null && !uponBitmap.isRecycled) {
        resultCanvas.drawBitmap(
            uponBitmap,
            null,
            outputRect,
            normalPaint
        )
    }

    if (!contentLayer.isRecycled) {
        contentLayer.recycle()
    }

    if (
        shapeMask != null &&
        !shapeMask.isRecycled
    ) {
        shapeMask.recycle()
    }

    return resultBitmap
}
