package com.example.ardogdemo.ui

import com.example.ardogdemo.domain.character.AccessoryId
import com.example.ardogdemo.domain.character.ModelTransform
import com.example.ardogdemo.domain.mission.CombatSound
import com.example.ardogdemo.domain.mission.ENEMY_MOVE_DELAY_MS
import com.example.ardogdemo.domain.mission.EnemyAction
import com.example.ardogdemo.domain.mission.EnemyKind
import com.example.ardogdemo.domain.mission.EnemyState
import com.example.ardogdemo.domain.mission.MissionCatalog
import com.example.ardogdemo.domain.mission.MissionCombat
import com.example.ardogdemo.domain.mission.MissionId
import com.example.ardogdemo.domain.mission.MissionPhase
import com.example.ardogdemo.domain.mission.MissionState
import com.example.ardogdemo.presentation.ArDogIntent
import com.example.ardogdemo.presentation.ArDogReducer
import com.example.ardogdemo.presentation.ArDogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionCombatTest {
    @Test fun `mission catalog contains exact required counts and enemy hp`() {
        val roaches = MissionCatalog.definition(MissionId.KillRoaches)
        val bobrito = MissionCatalog.definition(MissionId.DefeatBobrito)
        assertEquals(3, roaches.requiredKills)
        assertEquals(3, roaches.spawnPoints.size)
        assertEquals(1, roaches.enemyKind.maxHp)
        assertEquals(1, bobrito.requiredKills)
        assertEquals(7, bobrito.enemyKind.maxHp)
    }

    @Test fun `punch misses outside range`() {
        val state = active(EnemyState(0, EnemyKind.Cockroach, 0f, -.7f))
        assertEquals(state, MissionCombat.punch(state, ModelTransform(yaw = 0f)))
    }

    @Test fun `punch misses enemy behind player`() {
        val state = active(EnemyState(0, EnemyKind.Cockroach, 0f, .2f))
        assertEquals(state, MissionCombat.punch(state, ModelTransform(yaw = 0f)))
    }

    @Test fun `punch hits nearest enemy in range and facing cone`() {
        val near = EnemyState(0, EnemyKind.Bobrito, 0f, -.2f)
        val far = EnemyState(1, EnemyKind.Bobrito, .1f, -.35f)
        val result = MissionCombat.punch(active(near, far), ModelTransform(yaw = 0f))
        assertEquals(6, result.enemies[0].hp)
        assertEquals(7, result.enemies[1].hp)
        assertEquals(EnemyAction.TakeHit, result.enemies[0].action)
    }

    @Test fun `each roach owns hp and third kill wins`() {
        var state = MissionCombat.start(MissionId.KillRoaches).copy(
            enemies = listOf(
                EnemyState(0, EnemyKind.Cockroach, 0f, -.2f),
                EnemyState(1, EnemyKind.Cockroach, .1f, -.2f),
                EnemyState(2, EnemyKind.Cockroach, -.1f, -.2f),
            ),
        )
        state = MissionCombat.punch(state, ModelTransform(yaw = 0f))
        assertEquals(1, state.kills)
        state = MissionCombat.punch(state, ModelTransform(yaw = 0f))
        assertEquals(2, state.kills)
        state = MissionCombat.punch(state, ModelTransform(yaw = 0f))
        assertEquals(MissionPhase.Victory, state.phase)
        assertEquals(3, state.kills)
    }

    @Test fun `enemy pursues after source move delay`() {
        val enemy = EnemyState(0, EnemyKind.Bobrito, 0f, .8f)
        val result = MissionCombat.tick(active(enemy).copy(elapsedMs = ENEMY_MOVE_DELAY_MS), ModelTransform(), 100)
        assertTrue(result.enemies.single().y < enemy.y)
        assertEquals(EnemyAction.Move, result.enemies.single().action)
    }

    @Test fun `enemy attack respects player invulnerability`() {
        val enemy = EnemyState(0, EnemyKind.Bobrito, 0f, .1f)
        val first = MissionCombat.tick(active(enemy), ModelTransform(), 50)
        assertEquals(90, first.playerHp)
        val second = MissionCombat.tick(first.copy(enemies = listOf(first.enemies.single().copy(nextAttackAtMs = 0))), ModelTransform(), 50)
        assertEquals(90, second.playerHp)
        val afterWindow = MissionCombat.tick(
            second.copy(elapsedMs = second.invulnerableUntilMs, enemies = listOf(second.enemies.single().copy(nextAttackAtMs = 0))),
            ModelTransform(),
            50,
        )
        assertEquals(80, afterWindow.playerHp)
    }

    @Test fun `ticker does not overwrite active take hit reaction`() {
        val enemy = EnemyState(
            id = 0,
            kind = EnemyKind.Bobrito,
            x = 0f,
            y = .1f,
            action = EnemyAction.TakeHit,
            actionToken = 4,
            actionUntilMs = 350,
        )
        val result = MissionCombat.tick(active(enemy), ModelTransform(), 50)
        assertEquals(EnemyAction.TakeHit, result.enemies.single().action)
        assertEquals(4, result.enemies.single().actionToken)
        assertEquals(100, result.playerHp)
    }

    @Test fun `zero hp enters defeat`() {
        val state = active(EnemyState(0, EnemyKind.Bobrito, 0f, .1f)).copy(playerHp = 10)
        val result = MissionCombat.tick(state, ModelTransform(), 50)
        assertEquals(0, result.playerHp)
        assertEquals(MissionPhase.Defeat, result.phase)
        assertEquals(CombatSound.Defeat, result.sound)
    }

    @Test fun `mission lifecycle preserves player presentation`() {
        val initial = ArDogState(
            transform = ModelTransform(.4f, -.3f, 1.4f, 90f),
            mouthAccessory = AccessoryId.Pipe,
            eyeAccessory = AccessoryId.SpiralGlasses,
        )
        val started = ArDogReducer.reduce(initial, ArDogIntent.StartMission)
        val replayed = ArDogReducer.reduce(started.copy(mission = started.mission.copy(phase = MissionPhase.Victory)), ArDogIntent.ReplayMission)
        val exited = ArDogReducer.reduce(replayed.copy(mission = replayed.mission.copy(phase = MissionPhase.Defeat)), ArDogIntent.ExitMission)
        listOf(started, replayed, exited).forEach {
            assertEquals(initial.transform, it.transform)
            assertEquals(initial.mouthAccessory, it.mouthAccessory)
            assertEquals(initial.eyeAccessory, it.eyeAccessory)
        }
    }

    private fun active(vararg enemies: EnemyState) = MissionState(
        selected = if (enemies.firstOrNull()?.kind == EnemyKind.Cockroach) MissionId.KillRoaches else MissionId.DefeatBobrito,
        phase = MissionPhase.Active,
        enemies = enemies.toList(),
    )
}
