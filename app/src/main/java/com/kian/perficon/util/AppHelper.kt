package com.kian.perficon.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
)

fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)
    
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    return resolveInfos.map { resolveInfo ->
        AppInfo(
            name = resolveInfo.loadLabel(pm).toString(),
            packageName = resolveInfo.activityInfo.packageName,
            activityName = resolveInfo.activityInfo.name,
            icon = resolveInfo.loadIcon(pm)
        )
    }.sortedBy { it.name }
}
