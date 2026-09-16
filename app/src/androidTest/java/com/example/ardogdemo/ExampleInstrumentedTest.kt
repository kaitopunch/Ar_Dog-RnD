package com.example.ardogdemo

import android.Manifest
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import com.example.ardogdemo.diagnostics.RuntimeDiagnostics
import com.example.ardogdemo.diagnostics.RuntimeMetric
import com.example.ardogdemo.diagnostics.PerformanceScenarioController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun runtimeDiagnosticsExposeTypedCounters() {
        RuntimeDiagnostics.reset()
        RuntimeDiagnostics.mark(RuntimeMetric.CameraBindRequested)
        RuntimeDiagnostics.mark(RuntimeMetric.ModelInstanceCreated, 6)
        repeat(101) { index ->
            RuntimeDiagnostics.onSceneFrame((index + 1) * 16_666_667L)
        }

        val snapshot = RuntimeDiagnostics.snapshot()

        assertEquals(1L, snapshot.counters[RuntimeMetric.CameraBindRequested])
        assertEquals(6L, snapshot.counters[RuntimeMetric.ModelInstanceCreated])
        assertEquals(100, snapshot.frames.sampleCount)
        assertEquals(100, snapshot.frames.withinThirtyFpsCount)
    }

    @Test
    fun cameraAndScenePublishRuntimeSignals() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${instrumentation.targetContext.packageName} ${Manifest.permission.CAMERA}",
        ).close()
        RuntimeDiagnostics.reset()

        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(SCENE_WARM_UP_MS)
            val snapshot = RuntimeDiagnostics.snapshot()
            check(snapshot.counters.getValue(RuntimeMetric.CameraBindRequested) >= 1L)
            check(snapshot.counters.getValue(RuntimeMetric.ModelRequested) >= 1L)
            check(snapshot.frames.sampleCount > 0)
            instrumentation.runOnMainSync(PerformanceScenarioController::teardown)
            SystemClock.sleep(RESOURCE_RELEASE_WAIT_MS)
            val disposed = RuntimeDiagnostics.snapshot()
            check(disposed.counters.getValue(RuntimeMetric.CameraClosed) >= 1L)
            assertEquals(
                disposed.counters.getValue(RuntimeMetric.ModelAssetCreated),
                disposed.counters.getValue(RuntimeMetric.ModelAssetDestroyed),
            )
            assertEquals(
                disposed.counters.getValue(RuntimeMetric.ModelInstanceCreated),
                disposed.counters.getValue(RuntimeMetric.ModelInstanceDestroyed),
            )
        }
    }
}

private const val SCENE_WARM_UP_MS = 10_000L
private const val RESOURCE_RELEASE_WAIT_MS = 3_000L
