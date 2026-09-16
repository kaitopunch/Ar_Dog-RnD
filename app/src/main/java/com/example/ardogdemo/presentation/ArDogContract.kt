package com.example.ardogdemo.presentation

import com.example.ardogdemo.domain.character.AccessoryId
import com.example.ardogdemo.domain.character.CharacterAction
import com.example.ardogdemo.domain.character.CharacterReadiness
import com.example.ardogdemo.domain.character.ModelTransform
import com.example.ardogdemo.domain.character.assetName
import com.example.ardogdemo.domain.mission.MissionId
import com.example.ardogdemo.domain.mission.MissionState

data class ArDogState(
    val readiness: CharacterReadiness = CharacterReadiness.Loading,
    val transform: ModelTransform = ModelTransform(),
    val mouthAccessory: AccessoryId? = null,
    val eyeAccessory: AccessoryId? = null,
    val selectorOpen: Boolean = false,
    val action: CharacterAction = CharacterAction.Idle,
    val actionToken: Long = 0,
    val isMoving: Boolean = false,
    val multiModelRemainingMs: Long = 0L,
    val mission: MissionState = MissionState(),
    val errorMessage: String? = null,
) {
    val isMultiModelActive: Boolean get() = multiModelRemainingMs > 0L
    val playerInstanceCount: Int get() = if (isMultiModelActive) MULTI_MODEL_INSTANCE_COUNT else 1

    val modelPath: String get() = buildList {
        mouthAccessory?.let { add(it.assetName()) }
        eyeAccessory?.let { add(it.assetName()) }
    }.joinToString("_").ifEmpty { "base" }.let { "models/gugugaga/$it.glb" }
}

sealed interface ArDogIntent {
    data object CharacterReady : ArDogIntent
    data class CharacterFailed(val message: String) : ArDogIntent
    data class MoveBy(val x: Float, val y: Float) : ArDogIntent
    data object MovementStarted : ArDogIntent
    data object MovementStopped : ArDogIntent
    data class SetScale(val value: Float) : ArDogIntent
    data class RotateBy(val degrees: Float) : ArDogIntent
    data object ToggleSelector : ArDogIntent
    data class ToggleAccessory(val id: AccessoryId) : ArDogIntent
    data class PlayAction(val action: CharacterAction) : ArDogIntent
    data class ActionCompleted(val token: Long) : ArDogIntent
    data object ActivateMultiModel : ArDogIntent
    data class MultiModelTick(val deltaMs: Long) : ArDogIntent
    data class SelectMission(val id: MissionId) : ArDogIntent
    data object StartMission : ArDogIntent
    data object ReplayMission : ArDogIntent
    data object ExitMission : ArDogIntent
    data class CombatTick(val deltaMs: Long) : ArDogIntent
}

const val MULTI_MODEL_DURATION_MS = 60_000L
const val MULTI_MODEL_INSTANCE_COUNT = 6
