package com.kian.perficontemplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kian.perficontemplate.model.DynamicEntry
import com.kian.perficontemplate.model.IconEntry
import com.kian.perficontemplate.model.IconPackParser
import com.kian.perficontemplate.model.PackMeta
import com.kian.perficontemplate.ui.AboutScreen
import com.kian.perficontemplate.ui.IconsScreen
import com.kian.perficontemplate.ui.theme.PerficonTemplateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PerficonTemplateTheme {
                IconPackApp()
            }
        }
    }
}

@Composable
private fun dynamicStringResource(context: android.content.Context, name: String, default: String): String {
    return remember(name) {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id != 0) context.getString(id) else default
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPackApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var meta by remember { mutableStateOf(PackMeta("Icon Pack", "")) }
    var icons by remember { mutableStateOf(emptyList<IconEntry>()) }
    var dynamics by remember { mutableStateOf(emptyList<DynamicEntry>()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            meta = IconPackParser.parseMeta(context)
            val (i, d) = IconPackParser.parseIcons(context)
            icons = i
            dynamics = d
        }
        loaded = true
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        dynamicStringResource(context, "tab_about", "关于"),
        dynamicStringResource(context, "tab_icons", "图标")
    )

    Scaffold(
        topBar = {
            // Retro-style top bar: name of the pack, pixel font, 1dp bottom border
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = meta.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                // Retro tab row with 1dp border bottom
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
                ) {
                    tabs.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> AboutScreen(meta = meta)
                1 -> {
                    if (!loaded) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        IconsScreen(icons = icons, dynamics = dynamics)
                    }
                }
            }
        }
    }
}
