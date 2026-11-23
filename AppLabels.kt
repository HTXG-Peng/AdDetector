package com.example.addetector

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object AppLabels {

    private val cache = ConcurrentHashMap<String, String>()

    fun of(context: Context, pkg: String?): String {
        if (pkg.isNullOrEmpty()) return context.getString(R.string.start_label)

        return cache.getOrPut(pkg) {
            try {
                val pm = context.packageManager
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (_: Exception) {
                pkg
            }
        }
    }
}
