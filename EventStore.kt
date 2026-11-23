package com.example.addetector

import java.util.concurrent.CopyOnWriteArraySet

/**
 * 进程内的最近跳转记录。Activity 关闭时服务也会继续往里放，
 * 重新打开界面时可以直接看到最近 300 条。
 */
object EventStore {

    private const val MAX_EVENTS = 300

    private val lock = Any()
    private val events = ArrayDeque<JumpEvent>()
    private val listeners = CopyOnWriteArraySet<(JumpEvent) -> Unit>()

    fun add(event: JumpEvent) {
        synchronized(lock) {
            events.addLast(event)
            while (events.size > MAX_EVENTS) {
                events.removeFirst()
            }
        }

        listeners.forEach { listener ->
            try {
                listener(event)
            } catch (_: Exception) {
                // 单个监听器异常不影响服务本身。
            }
        }
    }

    fun snapshot(): List<JumpEvent> = synchronized(lock) { events.toList() }

    /** 把日志文件里的历史记录合并进来，按时间排序并去重。 */
    fun seed(history: List<JumpEvent>) {
        if (history.isEmpty()) return

        synchronized(lock) {
            val merged = (history + events)
                .distinctBy { "${it.time}-${it.from}-${it.to}" }
                .sortedBy { it.time }
                .takeLast(MAX_EVENTS)

            events.clear()
            events.addAll(merged)
        }
    }

    fun clear() {
        synchronized(lock) { events.clear() }
    }

    fun addListener(listener: (JumpEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (JumpEvent) -> Unit) {
        listeners.remove(listener)
    }
}
