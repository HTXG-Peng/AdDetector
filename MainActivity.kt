package com.example.addetector

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var emptyView: TextView
    private lateinit var listView: ListView
    private lateinit var accessibilityButton: Button
    private lateinit var adapter: JumpAdapter

    private val items = mutableListOf<JumpEvent>()
    private val handler = Handler(Looper.getMainLooper())

    private val statusTick = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val onJump: (JumpEvent) -> Unit = { event ->
        items.add(0, event)
        if (items.size > MAX_UI_EVENTS) {
            items.removeAt(items.lastIndex)
        }
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        emptyView = findViewById(R.id.empty)
        listView = findViewById(R.id.list)
        accessibilityButton = findViewById(R.id.btn_accessibility)

        adapter = JumpAdapter(this, items)
        listView.adapter = adapter

        accessibilityButton.setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.btn_files).setOnClickListener { openAllFilesSettings() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { confirmClear() }

        Notifications.ensureChannel(this)
        requestNotificationPermission()
        loadHistory()
    }

    override fun onStart() {
        super.onStart()
        EventStore.addListener(onJump)
        refreshFromStore()
        handler.post(statusTick)
    }

    override fun onStop() {
        super.onStop()
        EventStore.removeListener(onJump)
        handler.removeCallbacks(statusTick)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadHistory() {
        Thread {
            val parsed = LogWriter.readRecent(this, MAX_UI_EVENTS)
                .mapNotNull { JumpEvent.parseLine(it) }

            if (parsed.isNotEmpty()) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        EventStore.seed(parsed)
                        refreshFromStore()
                    }
                }
            }
        }.start()
    }

    private fun refreshFromStore() {
        items.clear()
        items.addAll(EventStore.snapshot().asReversed().take(MAX_UI_EVENTS))
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = items.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE

        if (empty) {
            emptyView.setText(
                if (isAccessibilityEnabled()) {
                    R.string.empty_waiting
                } else {
                    R.string.empty_disabled
                }
            )
        }
    }

    private fun refreshStatus() {
        val enabled = isAccessibilityEnabled()
        statusView.setText(if (enabled) R.string.status_on else R.string.status_off)
        statusView.setTextColor(getColor(if (enabled) R.color.status_on else R.color.status_off))
        accessibilityButton.setText(
            if (enabled) R.string.btn_accessibility_on else R.string.btn_accessibility_off
        )
        updateEmptyState()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val masterEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!masterEnabled) return false

        val component = ComponentName(this, AdDetectorService::class.java)
        val services = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return services.split(':').any { item ->
            item.equals(component.flattenToString(), ignoreCase = true) ||
                item.equals(component.flattenToShortString(), ignoreCase = true)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val prefs = getSharedPreferences("ad_detector", MODE_PRIVATE)
        if (prefs.getBoolean("notification_requested", false)) return

        prefs.edit().putBoolean("notification_requested", true).apply()
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS
        )
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(appIntent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.files_already, Toast.LENGTH_SHORT).show()
            return
        }

        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_STORAGE
        )
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_title)
            .setMessage(R.string.clear_message)
            .setPositiveButton(R.string.clear_confirm) { _, _ -> clearAll() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearAll() {
        EventStore.clear()
        items.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()

        Thread { LogWriter.clear(this) }.start()
        Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MAX_UI_EVENTS = 300
        private const val REQUEST_NOTIFICATIONS = 100
        private const val REQUEST_STORAGE = 101
    }
}
