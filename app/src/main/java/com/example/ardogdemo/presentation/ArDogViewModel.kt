package com.example.ardogdemo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ardogdemo.domain.character.CharacterAction
import com.example.ardogdemo.domain.mission.MissionPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

class ArDogViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(ArDogState())
    val state: StateFlow<ArDogState> = mutableState.asStateFlow()
    private var completionJob: Job? = null
    private var combatJob: Job? = null
    private var multiModelJob: Job? = null

    fun onIntent(intent: ArDogIntent) {
        if (intent == ArDogIntent.ResetPerformanceScenario) {
            completionJob?.cancel()
            completionJob = null
            stopCombatTicker()
            multiModelJob?.cancel()
            multiModelJob = null
        }
        mutableState.value = ArDogReducer.reduce(mutableState.value, intent)
        if (intent is ArDogIntent.PlayAction) scheduleCompletion(intent.action)
        if (intent == ArDogIntent.ActivateMultiModel) startMultiModelTicker()
        when (intent) {
            ArDogIntent.StartMission, ArDogIntent.ReplayMission -> startCombatTicker()
            ArDogIntent.ExitMission -> stopCombatTicker()
            else -> if (mutableState.value.mission.phase != MissionPhase.Active) stopCombatTicker()
        }
    }

    private fun startMultiModelTicker() {
        multiModelJob?.cancel()
        multiModelJob = viewModelScope.launch {
            var mark = TimeSource.Monotonic.markNow()
            while (mutableState.value.isMultiModelActive) {
                delay(MULTI_MODEL_TICK_MS)
                val elapsedMs = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)
                mark = TimeSource.Monotonic.markNow()
                mutableState.value = ArDogReducer.reduce(
                    mutableState.value,
                    ArDogIntent.MultiModelTick(elapsedMs),
                )
            }
        }
    }

    private fun scheduleCompletion(action: CharacterAction) {
        completionJob?.cancel()
        if (action.loops) return
        val token = mutableState.value.actionToken
        completionJob = viewModelScope.launch {
            delay(action.durationMs)
            mutableState.value = ArDogReducer.reduce(mutableState.value, ArDogIntent.ActionCompleted(token))
        }
    }

    private fun startCombatTicker() {
        combatJob?.cancel()
        combatJob = viewModelScope.launch {
            while (mutableState.value.mission.phase == MissionPhase.Active) {
                delay(COMBAT_TICK_MS)
                mutableState.value = ArDogReducer.reduce(mutableState.value, ArDogIntent.CombatTick(COMBAT_TICK_MS))
            }
        }
    }

    private fun stopCombatTicker() {
        combatJob?.cancel()
        combatJob = null
    }
}

private const val COMBAT_TICK_MS = 50L
private const val MULTI_MODEL_TICK_MS = 100L
