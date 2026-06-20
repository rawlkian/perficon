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

fun saveIconToInternalStorage(context: Context, uri: Uri, fileName: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val directory = File(context.filesDir, "icons")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    srcFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                "Download/$fileName"
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            srcFile.copyTo(destFile, overwrite = true)
            destFile.absolutePath
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
