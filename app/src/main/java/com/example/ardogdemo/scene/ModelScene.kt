package com.example.ardogdemo.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.example.ardogdemo.domain.character.ModelTransform
import com.example.ardogdemo.domain.mission.EnemyKind
import com.example.ardogdemo.domain.mission.EnemyState
import com.example.ardogdemo.presentation.ArDogState
import com.example.ardogdemo.presentation.MULTI_MODEL_INSTANCE_COUNT
import io.github.sceneview.SceneView
import io.github.sceneview.RenderQuality
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.model.model
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberModelLoader

@Composable
fun ModelScene(state: ArDogState, onReady: () -> Unit, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val loader = rememberModelLoader(engine)
    val playerInstances = rememberInstancedModels(
        loader = loader,
        path = state.modelPath,
        count = MULTI_MODEL_INSTANCE_COUNT,
    )
    val enemyKind = state.mission.enemies.firstOrNull()?.kind
    val enemyInstances = rememberInstancedModels(
        loader = loader,
        path = enemyKind?.modelPath,
        count = state.mission.enemies.size,
    )
    val playerNodes = remember(state.modelPath) { mutableStateMapOf<Int, ModelNode>() }
    var playerRoot by remember { mutableStateOf<Node?>(null) }
    val enemyNodes = remember { mutableStateMapOf<Int, ActiveEnemyNode>() }
    LaunchedEffect(playerRoot, state.transform) {
        playerRoot?.position = state.transform.toScenePosition()
        playerRoot?.rotation = Rotation(y = state.transform.yaw)
        playerRoot?.scale = Scale(state.transform.toSceneScale())
    }
    LaunchedEffect(playerNodes.size) {
        if (playerNodes.containsKey(0)) onReady()
    }
    LaunchedEffect(state.playerInstanceCount, playerNodes.size) {
        playerNodes.forEach { (index, node) ->
            val shouldBeVisible = index < state.playerInstanceCount
            if (node.isVisible != shouldBeVisible) {
                node.isVisible = shouldBeVisible
                node.stopPlayingAnimations()
                if (shouldBeVisible) {
                    node.playAnimation(state.action.clip, loop = state.action.loops)
                }
            }
        }
    }
    LaunchedEffect(playerNodes.size, state.modelPath, state.actionToken, state.action) {
        playerNodes.forEach { (index, model) ->
            model.stopPlayingAnimations()
            if (index < state.playerInstanceCount) {
                model.playAnimation(state.action.clip, loop = state.action.loops)
            }
        }
    }
    LaunchedEffect(state.mission.enemies.map { Triple(it.id, it.actionToken, it.action) }, enemyNodes.size) {
        val currentIds = state.mission.enemies.mapTo(mutableSetOf(), EnemyState::id)
        enemyNodes.keys.filterNot(currentIds::contains).forEach(enemyNodes::remove)
        state.mission.enemies.forEach { enemy ->
            enemyNodes[enemy.id]?.takeIf { it.kind == enemy.kind }?.node?.let { model ->
                model.playingAnimations.keys.toList().forEach(model::stopAnimation)
                model.playAnimation(enemy.action.clip, loop = enemy.action.loops)
            }
        }
    }
    SceneView(
        modifier = modifier, engine = engine, modelLoader = loader,
        surfaceType = SurfaceType.TextureSurface, isOpaque = false, autoFitContent = false,
        renderQuality = RenderQuality.Performance,
        cameraManipulator = rememberCameraManipulator(orbitHomePosition = Position(0f, .1f, 4.5f)),
    ) {
        Node(
            apply = {
                position = state.transform.toScenePosition()
                rotation = Rotation(y = state.transform.yaw)
                scale = Scale(state.transform.toSceneScale())
                playerRoot = this
            },
        ) {
            playerInstances.forEachIndexed { index, instance ->
                key(state.modelPath, index) {
                    Node(position = PLAYER_FORMATION[index]) {
                        ModelNode(
                            modelInstance = instance,
                            autoAnimate = false,
                            apply = {
                                isVisible = index < state.playerInstanceCount
                                isTouchable = index == 0
                                playerNodes[index] = this
                            },
                        )
                    }
                }
            }
        }
        state.mission.enemies.forEach { enemy ->
            val enemyInstance = enemyInstances.getOrNull(enemy.id)
            key(enemy.kind, enemy.id) {
                Node(
                    position = enemy.toScenePosition(),
                    rotation = Rotation(y = enemy.yaw),
                    scale = Scale(if (enemy.kind == EnemyKind.Bobrito) .28f else .16f),
                ) {
                    enemyInstance?.let {
                        ModelNode(
                            modelInstance = it,
                            autoAnimate = false,
                            apply = {
                                isTouchable = false
                                enemyNodes[enemy.id] = ActiveEnemyNode(enemy.kind, this)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun ModelNode.stopPlayingAnimations() {
    playingAnimations.keys.toList().forEach(::stopAnimation)
}

@Composable
private fun rememberInstancedModels(
    loader: io.github.sceneview.loaders.ModelLoader,
    path: String?,
    count: Int,
): List<com.google.android.filament.gltfio.FilamentInstance> {
    var instances by remember(path, count) {
        mutableStateOf<List<com.google.android.filament.gltfio.FilamentInstance>>(emptyList())
    }
    DisposableEffect(loader, path, count) {
        var disposed = false
        val job = if (path != null && count > 0) {
            loader.loadInstancedModelAsync(path, count) { loaded ->
                if (disposed) {
                    loaded.firstOrNull()?.let { loader.destroyModel(it.model) }
                } else {
                    instances = loaded
                }
            }
        } else {
            null
        }
        onDispose {
            disposed = true
            job?.cancel()
            instances.firstOrNull()?.let { loader.destroyModel(it.model) }
            instances = emptyList()
        }
    }
    return instances
}

private data class ActiveEnemyNode(val kind: EnemyKind, val node: ModelNode)

private val PLAYER_FORMATION = listOf(
    formationPosition(0f, 0f),
    formationPosition(-.40f, -FORMATION_ROW_DEPTH_STEP),
    formationPosition(.13f, -FORMATION_ROW_DEPTH_STEP),
    formationPosition(-.82f, -FORMATION_ROW_DEPTH_STEP * 2f),
    formationPosition(-.28f, -FORMATION_ROW_DEPTH_STEP * 2f),
    formationPosition(.28f, -FORMATION_ROW_DEPTH_STEP * 2f),
)

private fun ModelTransform.toScenePosition() = Position(x * .9f, -.25f + y * .7f, 0f)
private fun ModelTransform.toSceneScale() = scale * GUGUGAGA_BASE_SCALE
private fun EnemyState.toScenePosition() = Position(x * .9f, -.25f + y * .7f, .18f)

private fun formationPosition(x: Float, z: Float) = Position(
    x = x / GUGUGAGA_BASE_SCALE,
    y = 0f,
    z = z / GUGUGAGA_BASE_SCALE,
)

private const val GUGUGAGA_BASE_SCALE = .25f
private const val FORMATION_ROW_DEPTH_STEP = .20f
