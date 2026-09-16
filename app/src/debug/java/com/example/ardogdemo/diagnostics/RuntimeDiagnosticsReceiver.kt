package com.example.ardogdemo.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RuntimeDiagnosticsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESET_FRAMES -> RuntimeDiagnostics.resetFrames()
            ACTION_DUMP -> RuntimeDiagnostics.logSnapshot("adb_requested")
            ACTION_SET_SCENARIO -> PerformanceScenarioController.activate(
                intent.getStringExtra(PERFORMANCE_SCENARIO_EXTRA),
            )
            ACTION_TEARDOWN -> PerformanceScenarioController.teardown()
        }
    }
}

const val ACTION_RESET_FRAMES = "com.example.ardogdemo.diagnostics.RESET_FRAMES"
const val ACTION_DUMP = "com.example.ardogdemo.diagnostics.DUMP"
const val ACTION_SET_SCENARIO = "com.example.ardogdemo.diagnostics.SET_SCENARIO"
const val ACTION_TEARDOWN = "com.example.ardogdemo.diagnostics.TEARDOWN"
