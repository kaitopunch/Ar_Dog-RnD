package com.example.ardogdemo.presentation

import com.example.ardogdemo.domain.character.AccessorySlot
import com.example.ardogdemo.domain.character.CharacterAction
import com.example.ardogdemo.domain.character.CharacterReadiness
import com.example.ardogdemo.domain.mission.MissionCombat
import kotlin.math.PI
import kotlin.math.atan2

object ArDogReducer {
    fun reduce(state: ArDogState, intent: ArDogIntent): ArDogState = when (intent) {
        ArDogIntent.CharacterReady -> state.copy(readiness = CharacterReadiness.Ready, errorMessage = null)
        is ArDogIntent.CharacterFailed -> state.copy(readiness = CharacterReadiness.Failed, errorMessage = intent.message)
        is ArDogIntent.MoveBy -> state.copy(transform = state.transform.copy(
            x = (state.transform.x + intent.x).coerceIn(-1f, 1f),
            y = (state.transform.y + intent.y).coerceIn(-1f, 1f),
            yaw = movementYaw(intent.x, intent.y, state.transform.yaw),
        ))
        ArDogIntent.MovementStarted -> state.copy(
            action = CharacterAction.RunForward,
            actionToken = state.actionToken + 1,
            isMoving = true,
        )
        ArDogIntent.MovementStopped -> if (state.action == CharacterAction.RunForward) {
            state.copy(action = CharacterAction.Idle, actionToken = state.actionToken + 1, isMoving = false)
        } else {
            state.copy(isMoving = false)
        }
        is ArDogIntent.SetScale -> state.copy(transform = state.transform.copy(scale = intent.value.coerceIn(.55f, 1.6f)))
        is ArDogIntent.RotateBy -> state.copy(transform = state.transform.copy(yaw = wrap(state.transform.yaw + intent.degrees)))
        ArDogIntent.ToggleSelector -> state.copy(selectorOpen = !state.selectorOpen)
        is ArDogIntent.ToggleAccessory -> when (intent.id.slot) {
            AccessorySlot.Mouth -> state.copy(mouthAccessory = intent.id.takeUnless { state.mouthAccessory == it })
            AccessorySlot.Eyes -> state.copy(eyeAccessory = intent.id.takeUnless { state.eyeAccessory == it })
        }
        is ArDogIntent.PlayAction -> state.copy(
            action = intent.action,
            actionToken = state.actionToken + 1,
            mission = if (intent.action == CharacterAction.Punch) MissionCombat.punch(state.mission, state.transform) else state.mission,
        )
        is ArDogIntent.ActionCompleted -> if (intent.token == state.actionToken) state.copy(
            action = if (state.isMoving) CharacterAction.RunForward else CharacterAction.Idle,
            actionToken = if (state.isMoving) state.actionToken + 1 else state.actionToken,
        ) else state
        ArDogIntent.ActivateMultiModel -> state.copy(multiModelRemainingMs = MULTI_MODEL_DURATION_MS)
        is ArDogIntent.MultiModelTick -> state.copy(
            multiModelRemainingMs = (state.multiModelRemainingMs - intent.deltaMs.coerceAtLeast(0L)).coerceAtLeast(0L),
        )
        is ArDogIntent.SelectMission -> state.copy(mission = MissionCombat.select(state.mission, intent.id))
        ArDogIntent.StartMission -> state.copy(mission = MissionCombat.start(state.mission.selected), selectorOpen = false)
        ArDogIntent.ReplayMission -> state.copy(mission = MissionCombat.start(state.mission.selected))
        ArDogIntent.ExitMission -> state.copy(mission = MissionCombat.exit(state.mission))
        is ArDogIntent.CombatTick -> state.copy(mission = MissionCombat.tick(state.mission, state.transform, intent.deltaMs))
        ArDogIntent.ResetPerformanceScenario -> ArDogState(readiness = state.readiness)
    }

    private fun wrap(value: Float): Float = ((value % 360f) + 360f) % 360f

    private fun movementYaw(x: Float, y: Float, currentYaw: Float): Float {
        if (x == 0f && y == 0f) return currentYaw
        return wrap((atan2(x, -y) * 180f / PI.toFloat()))
    }
}
