package com.example.ardogdemo.diagnostics

import android.os.Build
import android.os.Trace
import android.util.Log
import com.example.ardogdemo.BuildConfig
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class RuntimeMetric {
    CameraBindRequested,
    CameraBound,
    CameraStreaming,
    CameraClosed,
    CameraError,
    ModelRequested,
    ModelLoaded,
    ModelCancelled,
    ModelAssetCreated,
    ModelAssetDestroyed,
    ModelInstanceCreated,
    ModelInstanceDestroyed,
    ModelVisibilityChanged,
}

data class FrameTimingSnapshot(
    val sampleCount: Int,
    val withinThirtyFpsCount: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
)

data class RuntimeSnapshot(
    val counters: Map<RuntimeMetric, Long>,
    val frames: FrameTimingSnapshot,
)

/** Bounded, allocation-free-on-frame diagnostics for device acceptance runs. */
object RuntimeDiagnostics {
    private const val TAG = "ArDogRuntime"
    private const val FRAME_CAPACITY = 3_600
    private val counters = RuntimeMetric.entries.associateWith { AtomicLong() }
    private val traceCookie = AtomicInteger()
    private val frameLogCounter = AtomicLong()
    private val frameCollector = SceneFrameCollector(FRAME_CAPACITY)

    val isEnabled: Boolean = BuildConfig.DEBUG

    fun mark(metric: RuntimeMetric, amount: Int = 1) {
        if (!isEnabled) return
        counters.getValue(metric).addAndGet(amount.toLong())
    }

    fun onSceneFrame(frameTimeNanos: Long) {
        if (!isEnabled) return
        frameCollector.add(frameTimeNanos)
        if (frameLogCounter.incrementAndGet() % FRAME_LOG_INTERVAL == 0L) {
            logSnapshot("scene_frame_checkpoint")
        }
    }

    fun beginAsyncTrace(name: String): Int {
        if (!isEnabled) return NO_TRACE_COOKIE
        val cookie = traceCookie.incrementAndGet()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.beginAsyncSection(name, cookie)
        }
        return cookie
    }

    fun endAsyncTrace(name: String, cookie: Int) {
        if (!isEnabled || cookie == NO_TRACE_COOKIE) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection(name, cookie)
        }
    }

    inline fun <T> trace(name: String, block: () -> T): T {
        if (!isEnabled) return block()
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    fun snapshot(): RuntimeSnapshot = RuntimeSnapshot(
        counters = counters.mapValues { it.value.get() },
        frames = frameCollector.snapshot(),
    )

    fun reset() {
        counters.values.forEach { it.set(0) }
        resetFrames()
    }

    fun resetFrames() {
        frameLogCounter.set(0)
        frameCollector.reset()
    }

    fun logSnapshot(reason: String) {
        if (!isEnabled) return
        val snapshot = snapshot()
        val counts = snapshot.counters.entries.joinToString(",") { (metric, value) ->
            "\"${metric.name}\":$value"
        }
        val frames = snapshot.frames
        Log.i(
            TAG,
            "{\"reason\":\"$reason\",\"counters\":{$counts}," +
                "\"frames\":{\"count\":${frames.sampleCount}," +
                "\"within30\":${frames.withinThirtyFpsCount}," +
                "\"p50Ms\":${frames.p50Ms},\"p95Ms\":${frames.p95Ms}," +
                "\"p99Ms\":${frames.p99Ms},\"maxMs\":${frames.maxMs}}}",
        )
    }
}

private const val NO_TRACE_COOKIE = -1
private const val FRAME_LOG_INTERVAL = 600L
