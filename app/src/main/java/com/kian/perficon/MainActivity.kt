package com.kian.perficon

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kian.perficon.ui.HomeScreen
import com.kian.perficon.ui.AppLanguage
import com.kian.perficon.ui.AppSettings
import com.kian.perficon.ui.LocalAppLanguage
import com.kian.perficon.ui.ProjectEditorScreen
import com.kian.perficon.ui.SettingsScreen
import com.kian.perficon.ui.editor.FastGeneratorScreen
import com.kian.perficon.ui.theme.PerficonTheme
import com.kian.perficon.viewmodel.IconPackViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appSettings = remember { AppSettings(applicationContext) }
            val language by appSettings.language.collectAsState()
            PerficonTheme {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    MainApp(appSettings)
                }
            }
        }
    }
}

@Composable
fun MainApp(appSettings: AppSettings) {
    val navController = rememberNavController()
    val viewModel: IconPackViewModel = viewModel()
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToProject = { projectId ->
                    navController.navigate("editor/$projectId")
                },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            val language by appSettings.language.collectAsState()
            SettingsScreen(
                language = language,
                onLanguageChange = appSettings::setLanguage,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "editor/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            ProjectEditorScreen(
                projectId = projectId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToGenerator = { iconName, targetPackage, targetActivity, isChange, mappingId ->
                    navController.navigate(
                        "generator/$projectId/${Uri.encode(iconName)}/${Uri.encode(targetPackage)}/" +
                            "${Uri.encode(targetActivity.ifBlank { "_" })}/$isChange/$mappingId"
                    )
                }
            )
        }
        composable(
            route = "generator/{projectId}/{iconName}/{targetPackage}/{targetActivity}/{isChange}/{mappingId}",
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("iconName") { type = NavType.StringType },
                navArgument("targetPackage") { type = NavType.StringType },
                navArgument("targetActivity") { type = NavType.StringType },
                navArgument("isChange") { type = NavType.BoolType },
                navArgument("mappingId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            val iconName = backStackEntry.arguments?.getString("iconName") ?: ""
            val targetPackage = backStackEntry.arguments?.getString("targetPackage") ?: ""
            val targetActivity = backStackEntry.arguments?.getString("targetActivity")?.takeUnless { it == "_" } ?: ""
            val isChange = backStackEntry.arguments?.getBoolean("isChange") ?: false
            val mappingId = backStackEntry.arguments?.getLong("mappingId") ?: 0L
            
            val projectState = viewModel.getProjectById(projectId).collectAsState(initial = null)
            val project = projectState.value ?: return@composable
            
            FastGeneratorScreen(
                iconName = iconName,
                project = project,
                onSave = { path ->
                    if (isChange && mappingId != 0L) {
                        scope.launch {
                            val mapping = viewModel.getMappingsForProject(projectId).first().find { it.id == mappingId }
                            if (mapping != null) {
                                viewModel.updateMapping(mapping.copy(iconPath = path))
                            }
                        }
                    } else {
                        viewModel.insertMapping(projectId, iconName, targetPackage, targetActivity, path)
                    }
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}
