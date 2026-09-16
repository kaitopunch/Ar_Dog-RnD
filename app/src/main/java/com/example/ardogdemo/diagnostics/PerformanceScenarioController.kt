package com.example.ardogdemo.diagnostics

import com.example.ardogdemo.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Debug-only scenario state used by adb-driven, repeatable performance captures. */
object PerformanceScenarioController {
    private val mutableScenario = MutableStateFlow<String?>(null)
    val scenario: StateFlow<String?> = mutableScenario.asStateFlow()

    fun activate(value: String?) {
        if (!BuildConfig.DEBUG) return
        mutableScenario.value = value?.takeIf(::isSupportedScenario)
    }

    fun teardown() {
        if (BuildConfig.DEBUG) mutableScenario.value = TEARDOWN
    }

    private fun isSupportedScenario(value: String): Boolean =
        value.matches(SCENARIO_PATTERN)
}

const val PERFORMANCE_SCENARIO_EXTRA = "performance_scenario"
const val PERFORMANCE_SCENARIO_TEARDOWN = "TEARDOWN"
private const val TEARDOWN = PERFORMANCE_SCENARIO_TEARDOWN
private val SCENARIO_PATTERN = Regex("S(?:[0-9]|1[0-2])")
