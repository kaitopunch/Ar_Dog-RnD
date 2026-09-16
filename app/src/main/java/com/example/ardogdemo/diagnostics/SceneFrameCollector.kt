package com.example.ardogdemo.diagnostics

internal class SceneFrameCollector(capacity: Int) {
    private val intervalsNanos = LongArray(capacity)
    private var previousFrameNanos = 0L
    private var writeIndex = 0
    private var size = 0

    @Synchronized
    fun add(frameTimeNanos: Long) {
        if (previousFrameNanos != 0L && frameTimeNanos > previousFrameNanos) {
            intervalsNanos[writeIndex] = frameTimeNanos - previousFrameNanos
            writeIndex = (writeIndex + 1) % intervalsNanos.size
            if (size < intervalsNanos.size) size++
        }
        previousFrameNanos = frameTimeNanos
    }

    @Synchronized
    fun snapshot(): FrameTimingSnapshot {
        if (size == 0) return FrameTimingSnapshot(0, 0, 0.0, 0.0, 0.0, 0.0)
        val sorted = LongArray(size) { index ->
            intervalsNanos[(writeIndex - size + index + intervalsNanos.size) % intervalsNanos.size]
        }.apply(LongArray::sort)
        return FrameTimingSnapshot(
            sampleCount = size,
            withinThirtyFpsCount = sorted.count { it <= THIRTY_FPS_FRAME_NANOS },
            p50Ms = sorted.percentileMs(.50),
            p95Ms = sorted.percentileMs(.95),
            p99Ms = sorted.percentileMs(.99),
            maxMs = sorted.last() / NANOS_PER_MILLISECOND,
        )
    }

    @Synchronized
    fun reset() {
        previousFrameNanos = 0L
        writeIndex = 0
        size = 0
    }
}

private fun LongArray.percentileMs(percentile: Double): Double {
    val index = ((size - 1) * percentile).toInt().coerceIn(indices)
    return this[index] / NANOS_PER_MILLISECOND
}

private const val THIRTY_FPS_FRAME_NANOS = 33_333_333L
private const val NANOS_PER_MILLISECOND = 1_000_000.0
