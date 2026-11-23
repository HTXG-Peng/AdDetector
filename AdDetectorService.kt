package com.example.addetector

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * 监听所有应用的前台窗口变化，并把“时间 + 从哪个包名到哪个包名 + 停留时长”
 * 写入日志、内存列表，并在疑似自动跳转时发通知。
 *
 * 不读取窗口内容，不联网。
 */
class AdDetectorService : AccessibilityService() {

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ad-detector-writer").apply { isDaemon = true }
    }

    private var currentPackage: String? = null
    private var currentSince: Long = 0L

    /** 最近一次用户点击的时间，用来区分“手动点开”和“疑似自动跳转”。 */
    private var lastClickAt: Long = 0L

    private var lastNotifyAt: Long = 0L

    private val homePackages: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Notifications.ensureChannel(this)

        val path = LogWriter.preferredFile(this).absolutePath
        Log.i(TAG, "Accessibility service connected. Log file: $path")
        enqueue(
            "# ---- service connected at ${JumpEvent.formatTimestamp(System.currentTimeMillis())} " +
                "-> $path"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val now = System.currentTimeMillis()

        when (ev.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val clicked = ev.packageName?.toString().orEmpty()
                if (clicked.isNotEmpty() &&
                    clicked != packageName &&
                    !isHomeOrSystem(clicked)
                ) {
                    lastClickAt = now
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChange(ev, now)
            }
        }
    }

    private fun handleWindowChange(event: AccessibilityEvent, now: Long) {
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == currentPackage) return

        val from = currentPackage
        val dwellMs = if (from == null) 0L else now - currentSince
        val auto = from != null && isSuspicious(from, pkg, dwellMs, now)

        currentPackage = pkg
        currentSince = now
        // 新应用成为前台后，之前的点击不再代表“在这个应用里的操作”。
        lastClickAt = 0L

        if (from == null) {
            if (pkg != packageName) {
                publish(JumpEvent(now, null, pkg, 0L, false))
            }
            return
        }

        // 进出本应用只更新状态，不记入跳转列表，避免自己干扰数据。
        if (from == packageName || pkg == packageName) return

        val jump = JumpEvent(now, from, pkg, dwellMs, auto)
        publish(jump)

        if (auto && now - lastNotifyAt >= NOTIFY_INTERVAL_MS) {
            lastNotifyAt = now
            Notifications.notifyAutoJump(this, jump)
        }
    }

    /**
     * 只在上一个应用停留时间很短、且最近没有用户点击时，才标记为“疑似自动跳转”。
     * 这是启发式判断，不是系统级证明。
     */
    private fun isSuspicious(from: String, to: String, dwellMs: Long, now: Long): Boolean {
        if (dwellMs <= 0L || dwellMs > AUTO_DWELL_MS) return false
        if (isHomeOrSystem(from) || isHomeOrSystem(to)) return false
        return now - lastClickAt > CLICK_GRACE_MS
    }

    private fun isHomeOrSystem(pkg: String): Boolean =
        pkg == "android" ||
            pkg == "com.android.systemui" ||
            pkg in homePackages

    private fun publish(event: JumpEvent) {
        enqueue(JumpEvent.formatLine(event))
        EventStore.add(event)
    }

    private fun enqueue(line: String) {
        try {
            writer.execute { LogWriter.append(this@AdDetectorService, line) }
        } catch (_: RejectedExecutionException) {
            // 服务正在销毁，丢弃最后几条日志即可。
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        enqueue("# ---- service destroyed at ${JumpEvent.formatTimestamp(System.currentTimeMillis())}")
        writer.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdDetector"
        private const val AUTO_DWELL_MS = 2_500L
        private const val CLICK_GRACE_MS = 3_000L
        private const val NOTIFY_INTERVAL_MS = 5_000L
    }
}
