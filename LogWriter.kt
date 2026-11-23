package com.example.addetector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * 优先写 /sdcard/AdDetector.log；
 * 没有“所有文件访问权限”时，自动退回到应用专属目录：
 * /sdcard/Android/data/com.example.addetector/files/AdDetector.log
 */
object LogWriter {

    private const val TAG = "AdDetector"
    private const val FILE_NAME = "AdDetector.log"

    fun preferredFile(context: Context): File =
        publicFile(context) ?: appFile(context)

    @Synchronized
    fun append(context: Context, line: String) {
        for (file in candidates(context)) {
            try {
                file.parentFile?.mkdirs()
                file.appendText(line + "\n", Charsets.UTF_8)
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Cannot write ${file.absolutePath}: ${t.message}")
            }
        }
        Log.e(TAG, "No writable log file for: $line")
    }

    @Synchronized
    fun readRecent(context: Context, limit: Int): List<String> {
        val merged = mutableListOf<String>()

        for (file in candidates(context)) {
            if (!file.exists()) continue

            val lines = try {
                file.readLines(Charsets.UTF_8)
            } catch (t: Throwable) {
                Log.w(TAG, "Cannot read ${file.absolutePath}: ${t.message}")
                continue
            }

            merged += lines.asReversed()
                .asSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .take(limit)
                .toList()
                .asReversed()
        }

        // 公开目录和应用专属目录可能先后都用过，合并后再按时间取最近 limit 条。
        return merged.distinct().sorted().takeLast(limit)
    }

    @Synchronized
    fun clear(context: Context) {
        for (file in candidates(context)) {
            try {
                if (file.exists()) {
                    file.writeText("", Charsets.UTF_8)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Cannot clear ${file.absolutePath}: ${t.message}")
            }
        }
    }

    private fun candidates(context: Context): List<File> =
        listOfNotNull(publicFile(context), appFile(context))

    private fun publicFile(context: Context): File? {
        val hasAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

        if (!hasAccess) return null

        val root = Environment.getExternalStorageDirectory() ?: return null
        return File(root, FILE_NAME)
    }

    private fun appFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }
}
