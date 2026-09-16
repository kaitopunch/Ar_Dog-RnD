package com.example.ardogdemo.ui

import com.example.ardogdemo.domain.character.AccessoryId
import com.example.ardogdemo.domain.character.CharacterAction
import com.example.ardogdemo.domain.character.CharacterReadiness
import com.example.ardogdemo.domain.character.ModelTransform
import com.example.ardogdemo.presentation.ArDogIntent
import com.example.ardogdemo.presentation.ArDogReducer
import com.example.ardogdemo.presentation.ArDogState
import com.example.ardogdemo.presentation.MULTI_MODEL_DURATION_MS
import com.example.ardogdemo.presentation.MULTI_MODEL_INSTANCE_COUNT
import org.junit.Assert.assertEquals
import org.junit.Test

class ArDogReducerTest {
    @Test fun `movement and scale are clamped`() {
        val moved = reduce(ArDogState(), ArDogIntent.MoveBy(4f, -4f))
        val scaled = reduce(moved, ArDogIntent.SetScale(8f))
        assertEquals(ModelTransform(1f, -1f, 1.6f, 45f), scaled.transform)
    }

    @Test fun `movement faces character in all four camera plane directions`() {
        assertEquals(0f, reduce(ArDogState(), ArDogIntent.MoveBy(0f, -1f)).transform.yaw)
        assertEquals(90f, reduce(ArDogState(), ArDogIntent.MoveBy(1f, 0f)).transform.yaw)
        assertEquals(180f, reduce(ArDogState(), ArDogIntent.MoveBy(0f, 1f)).transform.yaw)
        assertEquals(270f, reduce(ArDogState(), ArDogIntent.MoveBy(-1f, 0f)).transform.yaw)
    }

    @Test fun `zero movement preserves manually selected facing`() {
        val rotated = ArDogState(transform = ModelTransform(yaw = 135f))
        assertEquals(135f, reduce(rotated, ArDogIntent.MoveBy(0f, 0f)).transform.yaw)
    }

    @Test fun `accessories replace only within their slot`() {
        val pipe = reduce(ArDogState(), ArDogIntent.ToggleAccessory(AccessoryId.Pipe))
        val withEyes = reduce(pipe, ArDogIntent.ToggleAccessory(AccessoryId.BirthdayGlasses))
        val cigar = reduce(withEyes, ArDogIntent.ToggleAccessory(AccessoryId.Cigar))
        assertEquals(AccessoryId.Cigar, cigar.mouthAccessory)
        assertEquals(AccessoryId.BirthdayGlasses, cigar.eyeAccessory)
        assertEquals("models/gugugaga/cigar_birthday_glasses.glb", cigar.modelPath)
    }

    @Test fun `tapping selected accessory removes it`() {
        val selected = ArDogState(mouthAccessory = AccessoryId.Pipe)
        assertEquals(null, reduce(selected, ArDogIntent.ToggleAccessory(AccessoryId.Pipe)).mouthAccessory)
    }

    @Test fun `same action restarts with a new token`() {
        val first = reduce(ArDogState(), ArDogIntent.PlayAction(CharacterAction.Punch))
        val second = reduce(first, ArDogIntent.PlayAction(CharacterAction.Punch))
        assertEquals(2L, second.actionToken)
        assertEquals(CharacterAction.Punch, second.action)
    }

    @Test fun `joystick hold loops run and release returns to idle`() {
        val running = reduce(ArDogState(), ArDogIntent.MovementStarted)
        assertEquals(CharacterAction.RunForward, running.action)
        assertEquals(true, running.action.loops)

        val stopped = reduce(running, ArDogIntent.MovementStopped)
        assertEquals(CharacterAction.Idle, stopped.action)
        assertEquals(running.actionToken + 1, stopped.actionToken)
    }

    @Test fun `joystick release does not interrupt a button action`() {
        val running = reduce(ArDogState(), ArDogIntent.MovementStarted)
        val punch = reduce(running, ArDogIntent.PlayAction(CharacterAction.Punch))
        val released = reduce(punch, ArDogIntent.MovementStopped)
        assertEquals(CharacterAction.Punch, released.action)
        assertEquals(punch.actionToken, released.actionToken)
        assertEquals(false, released.isMoving)
    }

    @Test fun `joystick starts looping run and release returns to idle`() {
        val running = reduce(ArDogState(), ArDogIntent.MovementStarted)
        assertEquals(CharacterAction.RunForward, running.action)
        assertEquals(1L, running.actionToken)

        val stopped = reduce(running, ArDogIntent.MovementStopped)
        assertEquals(CharacterAction.Idle, stopped.action)
        assertEquals(2L, stopped.actionToken)
    }

    @Test fun `accessory selection preserves transform and running action`() {
        val running = ArDogState(
            transform = ModelTransform(.4f, -.3f, 1.4f, 25f),
            action = CharacterAction.RunForward,
            actionToken = 3L,
        )
        val equipped = reduce(running, ArDogIntent.ToggleAccessory(AccessoryId.Pipe))
        assertEquals(running.transform, equipped.transform)
        assertEquals(CharacterAction.RunForward, equipped.action)
        assertEquals(running.actionToken, equipped.actionToken)
    }

    @Test fun `stale completion cannot stop replacement action`() {
        val punch = reduce(ArDogState(), ArDogIntent.PlayAction(CharacterAction.Punch))
        val howl = reduce(punch, ArDogIntent.PlayAction(CharacterAction.Howl))
        assertEquals(howl, reduce(howl, ArDogIntent.ActionCompleted(punch.actionToken)))
        assertEquals(CharacterAction.Idle, reduce(howl, ArDogIntent.ActionCompleted(howl.actionToken)).action)
    }

    @Test fun `rotation wraps in both directions`() {
        assertEquals(350f, reduce(ArDogState(), ArDogIntent.RotateBy(-10f)).transform.yaw)
        assertEquals(10f, reduce(ArDogState(), ArDogIntent.RotateBy(370f)).transform.yaw)
    }

    @Test fun `multi model defaults to one and activation shows six for sixty seconds`() {
        val initial = ArDogState()
        assertEquals(1, initial.playerInstanceCount)

        val active = reduce(initial, ArDogIntent.ActivateMultiModel)
        assertEquals(MULTI_MODEL_DURATION_MS, active.multiModelRemainingMs)
        assertEquals(MULTI_MODEL_INSTANCE_COUNT, active.playerInstanceCount)
    }

    @Test fun `multi model countdown expires back to primary character`() {
        val active = reduce(ArDogState(), ArDogIntent.ActivateMultiModel)
        val nearlyExpired = reduce(active, ArDogIntent.MultiModelTick(59_900L))
        val expired = reduce(nearlyExpired, ArDogIntent.MultiModelTick(500L))

        assertEquals(100L, nearlyExpired.multiModelRemainingMs)
        assertEquals(0L, expired.multiModelRemainingMs)
        assertEquals(1, expired.playerInstanceCount)
    }

    @Test fun `tapping multi model while active restarts full duration`() {
        val active = reduce(ArDogState(), ArDogIntent.ActivateMultiModel)
        val elapsed = reduce(active, ArDogIntent.MultiModelTick(12_345L))
        val restarted = reduce(elapsed, ArDogIntent.ActivateMultiModel)
        assertEquals(MULTI_MODEL_DURATION_MS, restarted.multiModelRemainingMs)
    }

    @Test fun `multi model lifecycle preserves model transform action and mission`() {
        val source = ArDogState(
            transform = ModelTransform(.4f, -.3f, 1.4f, 25f),
            mouthAccessory = AccessoryId.Cigar,
            eyeAccessory = AccessoryId.BirthdayGlasses,
            action = CharacterAction.Dance,
            actionToken = 4L,
        )
        val active = reduce(source, ArDogIntent.ActivateMultiModel)
        val expired = reduce(active, ArDogIntent.MultiModelTick(MULTI_MODEL_DURATION_MS))

        assertEquals(source.transform, expired.transform)
        assertEquals(source.modelPath, expired.modelPath)
        assertEquals(source.action, expired.action)
        assertEquals(source.actionToken, expired.actionToken)
        assertEquals(source.mission, expired.mission)
    }

    @Test fun `performance reset clears workload state but preserves loaded readiness`() {
        val source = reduce(
            reduce(
                ArDogState(readiness = CharacterReadiness.Ready),
                ArDogIntent.ActivateMultiModel,
            ),
            ArDogIntent.ToggleAccessory(AccessoryId.Pipe),
        )

        val reset = reduce(source, ArDogIntent.ResetPerformanceScenario)

        assertEquals(CharacterReadiness.Ready, reset.readiness)
        assertEquals(ArDogState(readiness = reset.readiness), reset)
    }

    private fun reduce(state: ArDogState, intent: ArDogIntent) = ArDogReducer.reduce(state, intent)
}
