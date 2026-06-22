package com.kian.perficon.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import android.graphics.Bitmap

fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, fileName: String, projectId: Long = -1): String? {
    return try {
        val directory = if (projectId == -1L) {
            File(StorageHelper.rootDir, "Temp")
        } else {
            StorageHelper.getProjectIconsDir(projectId)
        }
        
        if (!directory.exists()) directory.mkdirs()
        
        val file = File(directory, fileName)
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Saves an icon to the project-specific icons directory in the public Perficon folder.
 */
fun saveIconToInternalStorage(context: Context, uri: Uri, fileName: String, projectId: Long = -1): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val directory = if (projectId == -1L) {
            // Fallback to internal if no project ID provided, or use a temp dir in Perficon
            File(StorageHelper.rootDir, "Temp")
        } else {
            StorageHelper.getProjectIconsDir(projectId)
        }
        
        if (!directory.exists()) directory.mkdirs()
        
        val file = File(directory, fileName)
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Copies a file to the public Download folder using MediaStore (Android 10+) 
 * or direct file access (legacy).
 */
fun exportFileToDownloads(context: Context, srcFile: File, fileName: String): String? {
    return try {
        val outputDir = StorageHelper.outputsDir
        val destInPerficon = File(outputDir, fileName)
        if (srcFile.canonicalFile != destInPerficon.canonicalFile) {
            srcFile.copyTo(destInPerficon, overwrite = true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = requireNotNull(
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ) { "Unable to create the Downloads entry." }

            try {
                requireNotNull(resolver.openOutputStream(uri)) { "Unable to open the Downloads entry." }.use { outputStream ->
                    destInPerficon.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, fileName).also { destination ->
                destInPerficon.copyTo(destination, overwrite = true)
            }
        }
        destInPerficon.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
