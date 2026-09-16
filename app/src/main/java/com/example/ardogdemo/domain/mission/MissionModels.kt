package com.example.ardogdemo.domain.mission

enum class MissionId { KillRoaches, DefeatBobrito }

enum class MissionPhase { Preview, Active, Victory, Defeat }

enum class EnemyKind(val modelPath: String, val maxHp: Int) {
    Cockroach("models/enemies/cockroach.glb", 1),
    Bobrito("models/enemies/bobrito_bandito.glb", 7),
}

enum class EnemyAction(val clip: String, val loops: Boolean) {
    Idle("Idle", true),
    Move("Move", true),
    Attack("Attack", false),
    TakeHit("TakeHit", false),
    Die("Die", false),
}

enum class CombatSound { EnemyHit1, EnemyHit2, EnemyHit3, EnemyDie, Victory, Defeat }

data class MissionDefinition(
    val id: MissionId,
    val requiredKills: Int,
    val enemyKind: EnemyKind,
    val spawnPoints: List<Pair<Float, Float>>,
)

data class EnemyState(
    val id: Int,
    val kind: EnemyKind,
    val x: Float,
    val y: Float,
    val yaw: Float = 180f,
    val hp: Int = kind.maxHp,
    val action: EnemyAction = EnemyAction.Idle,
    val actionToken: Long = 0,
    val actionUntilMs: Long = 0,
    val nextAttackAtMs: Long = 0,
) {
    val alive: Boolean get() = hp > 0
}

data class MissionState(
    val selected: MissionId = MissionId.DefeatBobrito,
    val phase: MissionPhase = MissionPhase.Preview,
    val elapsedMs: Long = 0,
    val playerHp: Int = PLAYER_MAX_HP,
    val invulnerableUntilMs: Long = 0,
    val kills: Int = 0,
    val enemies: List<EnemyState> = emptyList(),
    val sound: CombatSound? = null,
    val soundToken: Long = 0,
) {
    val definition: MissionDefinition get() = MissionCatalog.definition(selected)
}

object MissionCatalog {
    private val roaches = MissionDefinition(
        MissionId.KillRoaches,
        3,
        EnemyKind.Cockroach,
        listOf(-.72f to .58f, .68f to .62f, .08f to .92f),
    )
    private val bobrito = MissionDefinition(
        MissionId.DefeatBobrito,
        1,
        EnemyKind.Bobrito,
        listOf(-.55f to .65f),
    )

    fun definition(id: MissionId): MissionDefinition = when (id) {
        MissionId.KillRoaches -> roaches
        MissionId.DefeatBobrito -> bobrito
    }
}

const val PLAYER_MAX_HP = 100
const val ENEMY_DAMAGE = 10
const val PLAYER_PUNCH_DAMAGE = 1
const val PLAYER_ATTACK_RANGE = .62f
const val PLAYER_ATTACK_HALF_ANGLE = 45f
const val PLAYER_INVULNERABILITY_MS = 450L
const val ENEMY_ATTACK_COOLDOWN_MS = 1_000L
const val ENEMY_MOVE_DELAY_MS = 3_000L
