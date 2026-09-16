package com.example.ardogdemo.domain.mission

import com.example.ardogdemo.domain.character.ModelTransform
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object MissionCombat {
    fun select(state: MissionState, id: MissionId): MissionState =
        if (state.phase == MissionPhase.Preview) state.copy(selected = id) else state

    fun start(selected: MissionId): MissionState {
        val definition = MissionCatalog.definition(selected)
        return MissionState(
            selected = selected,
            phase = MissionPhase.Active,
            enemies = definition.spawnPoints.mapIndexed { index, point ->
                EnemyState(index, definition.enemyKind, point.first, point.second)
            },
        )
    }

    fun exit(state: MissionState): MissionState = MissionState(selected = state.selected)

    fun punch(state: MissionState, transform: ModelTransform): MissionState {
        if (state.phase != MissionPhase.Active) return state
        val target = state.enemies.asSequence()
            .filter(EnemyState::alive)
            .map { it to distance(transform.x, transform.y, it.x, it.y) }
            .filter { (enemy, distance) -> distance <= PLAYER_ATTACK_RANGE && facing(transform, enemy) }
            .minByOrNull { it.second }
            ?.first ?: return state
        val hp = (target.hp - PLAYER_PUNCH_DAMAGE).coerceAtLeast(0)
        val died = hp == 0
        val enemies = state.enemies.map { enemy ->
            if (enemy.id != target.id) enemy else enemy.copy(
                hp = hp,
                action = if (died) EnemyAction.Die else EnemyAction.TakeHit,
                actionToken = enemy.actionToken + 1,
                actionUntilMs = if (died) Long.MAX_VALUE else state.elapsedMs + ENEMY_HIT_REACTION_MS,
            )
        }
        val kills = state.kills + if (died) 1 else 0
        val won = kills >= state.definition.requiredKills
        return state.copy(
            enemies = enemies,
            kills = kills,
            phase = if (won) MissionPhase.Victory else MissionPhase.Active,
            sound = if (died) CombatSound.EnemyDie else hitSound(target.id, target.hp),
            soundToken = state.soundToken + 1,
        )
    }

    fun tick(state: MissionState, transform: ModelTransform, deltaMs: Long): MissionState {
        if (state.phase != MissionPhase.Active || deltaMs <= 0) return state
        val elapsed = state.elapsedMs + deltaMs.coerceAtMost(MAX_TICK_MS)
        var playerHp = state.playerHp
        var invulnerableUntil = state.invulnerableUntilMs
        var sound = state.sound
        var soundToken = state.soundToken
        val enemies = state.enemies.map { enemy ->
            if (!enemy.alive) return@map enemy
            if (enemy.action == EnemyAction.TakeHit && elapsed < enemy.actionUntilMs) return@map enemy
            val distance = distance(enemy.x, enemy.y, transform.x, transform.y)
            val attackRange = attackRange(enemy.kind)
            if (distance <= attackRange) {
                if (elapsed >= enemy.nextAttackAtMs) {
                    if (elapsed >= invulnerableUntil) {
                        playerHp = (playerHp - ENEMY_DAMAGE).coerceAtLeast(0)
                        invulnerableUntil = elapsed + PLAYER_INVULNERABILITY_MS
                        if (playerHp == 0) {
                            sound = CombatSound.Defeat
                            soundToken += 1
                        }
                    }
                    enemy.copy(
                        yaw = yawToward(enemy.x, enemy.y, transform.x, transform.y),
                        action = EnemyAction.Attack,
                        actionToken = enemy.actionToken + 1,
                        actionUntilMs = elapsed + ENEMY_ATTACK_ANIMATION_MS,
                        nextAttackAtMs = elapsed + ENEMY_ATTACK_COOLDOWN_MS,
                    )
                } else if (enemy.action == EnemyAction.Attack && elapsed < enemy.actionUntilMs) {
                    enemy
                } else {
                    enemy.withAction(EnemyAction.Idle)
                }
            } else if (elapsed < ENEMY_MOVE_DELAY_MS) {
                enemy
            } else {
                val step = moveSpeed(enemy.kind) * deltaMs.coerceAtMost(MAX_TICK_MS) / 1_000f
                val ratio = (step / distance).coerceAtMost(1f)
                enemy.copy(
                    x = enemy.x + (transform.x - enemy.x) * ratio,
                    y = enemy.y + (transform.y - enemy.y) * ratio,
                    yaw = yawToward(enemy.x, enemy.y, transform.x, transform.y),
                    action = EnemyAction.Move,
                    actionToken = if (enemy.action == EnemyAction.Move) enemy.actionToken else enemy.actionToken + 1,
                )
            }
        }
        return state.copy(
            elapsedMs = elapsed,
            playerHp = playerHp,
            invulnerableUntilMs = invulnerableUntil,
            enemies = enemies,
            phase = if (playerHp == 0) MissionPhase.Defeat else MissionPhase.Active,
            sound = sound,
            soundToken = soundToken,
        )
    }

    private fun EnemyState.withAction(next: EnemyAction): EnemyState =
        if (action == next) this else copy(action = next, actionToken = actionToken + 1)

    private fun facing(transform: ModelTransform, enemy: EnemyState): Boolean {
        val radians = transform.yaw * PI.toFloat() / 180f
        val facingX = sin(radians)
        val facingY = -cos(radians)
        val dx = enemy.x - transform.x
        val dy = enemy.y - transform.y
        val length = hypot(dx, dy)
        if (length == 0f) return true
        val dot = ((facingX * dx + facingY * dy) / length).coerceIn(-1f, 1f)
        return dot >= cos(PLAYER_ATTACK_HALF_ANGLE * PI.toFloat() / 180f)
    }

    private fun yawToward(fromX: Float, fromY: Float, toX: Float, toY: Float): Float =
        ((atan2(toX - fromX, -(toY - fromY)) * 180f / PI.toFloat()) + 360f) % 360f

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)
    private fun moveSpeed(kind: EnemyKind) = if (kind == EnemyKind.Bobrito) .18f else .12f
    private fun attackRange(kind: EnemyKind) = if (kind == EnemyKind.Bobrito) .55f else .25f
    private fun hitSound(enemyId: Int, hp: Int) = when ((enemyId + hp) % 3) {
        0 -> CombatSound.EnemyHit1
        1 -> CombatSound.EnemyHit2
        else -> CombatSound.EnemyHit3
    }
}

private const val ENEMY_HIT_REACTION_MS = 350L
private const val ENEMY_ATTACK_ANIMATION_MS = 650L
private const val MAX_TICK_MS = 100L
