package com.example.addetector

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一次前台应用切换。
 *
 * from 为 null 表示服务刚启动后的第一次前台应用。
 * auto 只是“疑似”：基于“上一个应用停留时间很短”和“最近没有点击事件”推断，
 * 无障碍服务无法 100% 证明跳转是否由广告自动触发。
 */
data class JumpEvent(
    val time: Long,
    val from: String?,
    val to: String,
    val dwellMs: Long,
    val auto: Boolean
) {
    companion object {
        private const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"
        private const val LEGACY_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
        private const val AUTO_MARK = "AUTO"

        fun formatTimestamp(time: Long): String =
            SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(time))

        fun formatLine(event: JumpEvent): String {
            val timestamp = formatTimestamp(event.time)
            val from = event.from ?: "-"
            val auto = if (event.auto) AUTO_MARK else "-"
            return "$timestamp\t$from\t${event.to}\t${event.dwellMs}\t$auto"
        }

        fun parseLine(line: String): JumpEvent? {
            val parts = line.split('\t')
            if (parts.size != 2 && parts.size < 5) return null
            return try {
                val time = parseTimestamp(parts[0]) ?: return null

                if (parts.size == 2) {
                    // 兼容旧版日志：时间 \t 包名
                    JumpEvent(time, null, parts[1], 0L, false)
                } else {
                    val from = parts[1].takeUnless { it == "-" }
                    val to = parts[2]
                    val dwell = parts[3].toLongOrNull() ?: 0L
                    JumpEvent(time, from, to, dwell, parts[4] == AUTO_MARK)
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun parseTimestamp(value: String): Long? {
            for (pattern in arrayOf(TIME_PATTERN, LEGACY_TIME_PATTERN)) {
                try {
                    val date = SimpleDateFormat(pattern, Locale.US).parse(value)
                    if (date != null) return date.time
                } catch (_: Exception) {
                    // 尝试下一种格式。
                }
            }
            return null
        }

        fun formatClock(time: Long): String =
            SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(time))

        fun formatDwell(ms: Long): String {
            if (ms <= 0L) return "—"
            return if (ms < 1_000L) {
                "${ms}ms"
            } else {
                String.format(Locale.US, "%.1fs", ms / 1000.0)
            }
        }
    }
}
