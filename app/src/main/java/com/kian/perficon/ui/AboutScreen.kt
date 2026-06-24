package com.kian.perficon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.kian.perficon.ui.components.RetroIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    RetroIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = localize("返回", LocalAppLanguage.current))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PixelWolfAnimation(
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Perficon",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "作者：Kian",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PixelWolfAnimation(modifier: Modifier = Modifier) {
    val frames = remember {
        listOf(
            listOf(
                "................",
                "....K......K....",
                "...K.K....K.K...",
                "..K.G.KKKK.G.K..",
                "..K.GGGGGGGG.K..",
                ".K.GGKGGGGKGG.K.",
                ".K.GGGGKKGGGG.K.",
                ".K.GGGGWWGGGG.K.",
                "..K.GGWWWWGG.K..",
                "...KKKWWWWKKK...",
                ".....KLLLLK.K.Y.",
                "....KLLLLLLK..Y.",
                "...KBBBBBBBBBK..",
                "..KBBBBBBBBBBBK.",
                ".KBBBBBBBBBBBBBK",
                "KKKKKKKKKKKKKKKK"
            ),
            listOf(
                "................",
                "....K......K....",
                "...K.K....K.K...",
                "..K.G.KKKK.G.K..",
                "..K.GGGGGGGG.K..",
                ".K.GGGGGGGGGG.K.",
                ".K.GGGGKKGGGG.K.",
                ".K.GGGGWWGGGG.K.",
                "..K.GGWWWWGG.K..",
                "...KKKWWWWKKK...",
                ".....KLLLLK.Y...",
                "....KLLLLLLKY...",
                "...KBBBBBBBBBK..",
                "..KBBBBBBBBBBBK.",
                ".KBBBBBBBBBBBBBK",
                "KKKKKKKKKKKKKKKK"
            ),
            listOf(
                "................",
                "....K......K....",
                "...K.K....K.K...",
                "..K.G.KKKK.G.K..",
                "..K.GGGGGGGG.K..",
                ".K.GGKKGGKKGG.K.",
                ".K.GGGGKKGGGG.K.",
                ".K.GGGGWWGGGG.K.",
                "..K.GGWWWWGG.K..",
                "...KKKWWWWKKK...",
                ".....KLLLLK...Y.",
                "....KLLLLLLK..Y.",
                "...KBBBBBBBBBK..",
                "..KBBBBBBBBBBBK.",
                ".KBBBBBBBBBBBBBK",
                "KKKKKKKKKKKKKKKK"
            )
        )
    }

    var frameIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400L)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    val colorMap = remember {
        mapOf(
            '.' to Color.Transparent,
            'K' to Color(0xFF181F2D),
            'W' to Color.White,
            'G' to Color(0xFF8B939F),
            'Y' to Color(0xFFF3C63F),
            'B' to Color(0xFFB57E4C),
            'L' to Color(0xFFE2E4E9)
        )
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val grid = frames[frameIndex]
        val rows = grid.size
        val cols = grid.firstOrNull()?.length ?: 1
        val pixelW = size.width / cols
        val pixelH = size.height / rows

        for (r in 0 until rows) {
            val rowStr = grid[r]
            for (c in 0 until cols) {
                if (c < rowStr.length) {
                    val char = rowStr[c]
                    val color = colorMap[char] ?: Color.Transparent
                    if (color != Color.Transparent) {
                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(c * pixelW, r * pixelH),
                            size = Size(pixelW + 0.1f, pixelH + 0.1f)
                        )
                    }
                }
            }
        }
    }
}
