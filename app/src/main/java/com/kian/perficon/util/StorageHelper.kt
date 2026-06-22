package com.kian.perficon.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

object StorageHelper {

    val rootDir: File
        get() = File(Environment.getExternalStorageDirectory(), "Perficon")

    val projectsDir: File
        get() = File(rootDir, "Projects").apply { if (!exists()) mkdirs() }

    val outputsDir: File
        get() = File(rootDir, "Outputs").apply { if (!exists()) mkdirs() }

    fun getProjectDir(projectId: Long): File {
        return File(projectsDir, "project_$projectId").apply { if (!exists()) mkdirs() }
    }

    fun getProjectIconsDir(projectId: Long): File {
        return File(getProjectDir(projectId), "icons").apply { if (!exists()) mkdirs() }
    }

    fun isStorageManager(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
}
