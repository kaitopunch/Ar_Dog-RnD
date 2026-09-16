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
import androidx.compose.ui.platform.testTag
import com.example.ardogdemo.diagnostics.PerformanceTestTags
import com.example.ardogdemo.diagnostics.RuntimeDiagnostics
import com.example.ardogdemo.diagnostics.RuntimeMetric
import com.example.ardogdemo.domain.character.ModelTransform
import com.example.ardogdemo.domain.mission.EnemyKind
import com.example.ardogdemo.domain.mission.EnemyState
import com.example.ardogdemo.presentation.ArDogState
import com.example.ardogdemo.presentation.MULTI_MODEL_INSTANCE_COUNT
import com.google.android.filament.gltfio.FilamentInstance
import io.github.sceneview.SceneView
import io.github.sceneview.RenderQuality
import io.github.sceneview.SurfaceType
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.model.model
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay

@Composable
fun ModelScene(state: ArDogState, onReady: () -> Unit, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val loader = rememberModelLoader(engine)
    val modelDestroyer = remember(loader) { DeferredModelDestroyer(loader) }
    DisposableEffect(modelDestroyer) {
        onDispose(modelDestroyer::onSceneDisposed)
    }
    var requestedPlayerPath by remember { mutableStateOf(state.modelPath) }
    LaunchedEffect(state.modelPath) {
        if (state.modelPath != requestedPlayerPath) {
            delay(MODEL_SWAP_DEBOUNCE_MS)
            requestedPlayerPath = state.modelPath
        }
    }
    val playerModel = rememberInstancedModels(
        loader = loader,
        destroyer = modelDestroyer,
        path = requestedPlayerPath,
        count = MULTI_MODEL_INSTANCE_COUNT,
    )
    val playerInstances = playerModel.instances
    val enemyKind = state.mission.enemies.firstOrNull()?.kind
    val enemyModel = rememberInstancedModels(
        loader = loader,
        destroyer = modelDestroyer,
        path = enemyKind?.modelPath,
        count = state.mission.enemies.size,
    )
    val enemyInstances = enemyModel.instances
    val playerNodes = remember(playerModel.path) { mutableStateMapOf<Int, ModelNode>() }
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
                RuntimeDiagnostics.mark(RuntimeMetric.ModelVisibilityChanged)
                node.stopPlayingAnimations()
                if (shouldBeVisible) {
                    node.playAnimation(state.action.clip, loop = state.action.loops)
                }
            }
        }
    }
    LaunchedEffect(playerNodes.size, playerModel.path, state.actionToken, state.action) {
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
        modifier = modifier.testTag(PerformanceTestTags.Scene),
        engine = engine,
        modelLoader = loader,
        surfaceType = SurfaceType.TextureSurface, isOpaque = false, autoFitContent = false,
        renderQuality = RenderQuality.Performance,
        cameraManipulator = rememberCameraManipulator(orbitHomePosition = Position(0f, .1f, 4.5f)),
        onFrame = { frameTimeNanos ->
            RuntimeDiagnostics.onSceneFrame(frameTimeNanos)
            modelDestroyer.onFrame()
        },
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
                key(playerModel.path, index) {
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
    loader: ModelLoader,
    destroyer: DeferredModelDestroyer,
    path: String?,
    count: Int,
): InstancedModelSet {
    var active by remember(loader) { mutableStateOf(InstancedModelSet()) }
    DisposableEffect(loader, destroyer) {
        onDispose {
            destroyer.enqueue(active.instances)
            active = InstancedModelSet()
        }
    }
    DisposableEffect(loader, path, count) {
        var disposed = false
        var completed = false
        val traceCookie = RuntimeDiagnostics.beginAsyncTrace("ArDogModelLoad")
        if (path != null && count > 0) {
            RuntimeDiagnostics.mark(RuntimeMetric.ModelRequested)
        }
        val job = if (path != null && count > 0) {
            loader.loadInstancedModelAsync(path, count) { loaded ->
                completed = true
                RuntimeDiagnostics.endAsyncTrace("ArDogModelLoad", traceCookie)
                RuntimeDiagnostics.mark(RuntimeMetric.ModelLoaded)
                if (loaded.isNotEmpty()) {
                    RuntimeDiagnostics.mark(RuntimeMetric.ModelAssetCreated)
                    RuntimeDiagnostics.mark(RuntimeMetric.ModelInstanceCreated, loaded.size)
                }
                if (disposed) {
                    destroyer.destroyUnattached(loaded)
                } else if (loaded.isNotEmpty()) {
                    val previous = active.instances
                    active = InstancedModelSet(path, loaded)
                    destroyer.enqueue(previous)
                }
            }
        } else {
            destroyer.enqueue(active.instances)
            active = InstancedModelSet()
            null
        }
        onDispose {
            disposed = true
            if (!completed && job != null) {
                RuntimeDiagnostics.mark(RuntimeMetric.ModelCancelled)
                RuntimeDiagnostics.endAsyncTrace("ArDogModelLoad", traceCookie)
            }
            job?.cancel()
        }
    }
    return active
}

private data class InstancedModelSet(
    val path: String? = null,
    val instances: List<FilamentInstance> = emptyList(),
)

/**
 * Removes model nodes from composition before destroying their shared Filament asset.
 * Filament may still reference the previous renderable for a few submitted frames, so
 * destroying it synchronously from a path-keyed DisposableEffect can invalidate the
 * renderer's native handle. SceneView uses the same three-frame grace period for its
 * deferred GPU resource destruction.
 */
private class DeferredModelDestroyer(private val loader: ModelLoader) {
    private val pending = ArrayDeque<PendingModelDestroy>()
    private var sceneDisposed = false

    fun enqueue(instances: List<FilamentInstance>) {
        if (instances.isEmpty()) return
        if (sceneDisposed) {
            recordDelegatedDestroy(instances)
        } else {
            pending.addLast(PendingModelDestroy(instances))
        }
    }

    fun destroyUnattached(instances: List<FilamentInstance>) {
        if (instances.isEmpty()) return
        if (sceneDisposed) {
            recordDelegatedDestroy(instances)
        } else {
            destroy(instances)
        }
    }

    fun onFrame() {
        if (sceneDisposed) return
        repeat(pending.size) {
            val item = pending.removeFirst()
            item.framesRemaining--
            if (item.framesRemaining == 0) {
                destroy(item.instances)
            } else {
                pending.addLast(item)
            }
        }
    }

    fun onSceneDisposed() {
        sceneDisposed = true
        while (pending.isNotEmpty()) {
            recordDelegatedDestroy(pending.removeFirst().instances)
        }
    }

    private fun destroy(instances: List<FilamentInstance>) {
        loader.destroyModel(instances.first().model)
        recordDestroy(instances)
    }

    private fun recordDelegatedDestroy(instances: List<FilamentInstance>) {
        // rememberModelLoader owns final teardown and destroys every model before Engine.
        recordDestroy(instances)
    }

    private fun recordDestroy(instances: List<FilamentInstance>) {
        RuntimeDiagnostics.mark(RuntimeMetric.ModelAssetDestroyed)
        RuntimeDiagnostics.mark(RuntimeMetric.ModelInstanceDestroyed, instances.size)
    }
}

private data class PendingModelDestroy(
    val instances: List<FilamentInstance>,
    var framesRemaining: Int = MODEL_DESTROY_GRACE_FRAMES,
)

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
private const val MODEL_DESTROY_GRACE_FRAMES = 3
private const val MODEL_SWAP_DEBOUNCE_MS = 250L
