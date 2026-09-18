package com.example.ardogdemo.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ardogdemo.R
import com.example.ardogdemo.audio.CharacterAudioPlayer
import com.example.ardogdemo.audio.CombatAudioPlayer
import com.example.ardogdemo.camera.CameraPreview
import com.example.ardogdemo.config.FirebaseMultiModelCountSource
import com.example.ardogdemo.domain.character.CharacterAction
import com.example.ardogdemo.domain.character.AccessoryId
import com.example.ardogdemo.domain.character.CharacterReadiness
import com.example.ardogdemo.domain.mission.CombatSound
import com.example.ardogdemo.domain.mission.MissionId
import com.example.ardogdemo.domain.mission.MissionPhase
import com.example.ardogdemo.diagnostics.PerformanceTestTags
import com.example.ardogdemo.diagnostics.PERFORMANCE_SCENARIO_TEARDOWN
import com.example.ardogdemo.diagnostics.PerformanceScenarioController
import com.example.ardogdemo.diagnostics.RuntimeDiagnostics
import com.example.ardogdemo.scene.ModelScene
import com.example.ardogdemo.ui.components.AccessorySelector
import com.example.ardogdemo.ui.components.FormationLayoutSelector
import com.example.ardogdemo.ui.components.ModelScaleSlider
import com.example.ardogdemo.ui.components.MultiModelSelector
import com.example.ardogdemo.ui.components.MovementJoystick
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun ArDogRoute(viewModel: ArDogViewModel = viewModel { ArDogViewModel(FirebaseMultiModelCountSource()) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val performanceScenario by PerformanceScenarioController.scenario.collectAsStateWithLifecycle()
    ArDogScreen(state, viewModel::onIntent, performanceScenario)
}

@Composable
fun ArDogScreen(
    state: ArDogState,
    onIntent: (ArDogIntent) -> Unit,
    performanceScenario: String? = null,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
        Box(
            Modifier.fillMaxSize().testTag(PerformanceTestTags.PermissionGate),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant_camera)) }
        }
        return
    }
    PerformanceScenarioDriver(performanceScenario, state, onIntent)
    if (performanceScenario == PERFORMANCE_SCENARIO_TEARDOWN) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("PERF:TEARDOWN", color = Color.White)
        }
        return
    }
    val audio = remember { CharacterAudioPlayer(context.applicationContext) }
    val combatAudio = remember { CombatAudioPlayer(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose {
            RuntimeDiagnostics.logSnapshot("screen_disposed")
            audio.release()
            combatAudio.release()
        }
    }
    LaunchedEffect(state.actionToken, state.action) {
        audio.stop()
        if (state.action == CharacterAction.Howl) { delay(43); audio.playHowl() }
    }
    LaunchedEffect(state.mission.soundToken) {
        state.mission.sound?.let(combatAudio::play)
    }
    LaunchedEffect(state.mission.phase) {
        if (state.mission.phase == MissionPhase.Victory) {
            delay(350)
            combatAudio.play(CombatSound.Victory)
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag(PerformanceTestTags.Screen),
    ) {
        CameraPreview(Modifier.fillMaxSize())
        if (performanceScenario != "S0") {
            ModelScene(state, { onIntent(ArDogIntent.CharacterReady) }, Modifier.fillMaxSize())
        }
        MissionOverlay(
            state.mission,
            onIntent,
            Modifier.align(Alignment.TopCenter).padding(top = 22.dp).fillMaxSize(.92f),
        )
        Column(
            Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MultiModelSelector(
                icon = painterResource(R.drawable.clone_jutsu),
                contentDescription = stringResource(R.string.activate_multi_model),
                remainingMs = state.multiModelRemainingMs,
                durationMs = MULTI_MODEL_DURATION_MS,
                enabled = state.readiness == CharacterReadiness.Ready,
                onClick = { onIntent(ArDogIntent.ActivateMultiModel) },
                modifier = Modifier.testTag(PerformanceTestTags.MultiModel),
            )
            FormationLayoutSelector(
                selected = state.formationLayout,
                enabled = state.readiness == CharacterReadiness.Ready,
                onSelect = { onIntent(ArDogIntent.SelectFormationLayout(it)) },
            )
        }
        if (!state.selectorOpen) ModelGestureLayer(onIntent, Modifier.align(Alignment.Center).size(260.dp, 430.dp))
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModelScaleSlider(state.transform.scale, state.readiness == CharacterReadiness.Ready, onValueChange = { onIntent(ArDogIntent.SetScale(it)) })
            MovementJoystick(
                enabled = state.readiness == CharacterReadiness.Ready,
                onMove = { x, y -> onIntent(ArDogIntent.MoveBy(x, y)) },
                onMoveStateChanged = { moving ->
                    onIntent(if (moving) ArDogIntent.MovementStarted else ArDogIntent.MovementStopped)
                },
            )
        }
        ActionControls(onIntent, Modifier.align(Alignment.CenterEnd).padding(end = 18.dp))
        if (state.selectorOpen) AccessorySelector(
            selected = setOfNotNull(state.mouthAccessory, state.eyeAccessory),
            onSelect = { onIntent(ArDogIntent.ToggleAccessory(it)) },
            onClose = { onIntent(ArDogIntent.ToggleSelector) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp).size(250.dp, 330.dp),
        )
        MissionResultOverlay(state.mission.phase, onIntent, Modifier.fillMaxSize())
        performanceScenario?.let { scenario ->
            Text(
                "PERF:$scenario",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
    }
}

@Composable
private fun PerformanceScenarioDriver(
    scenario: String?,
    state: ArDogState,
    onIntent: (ArDogIntent) -> Unit,
) {
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(scenario) {
        if (scenario == null || scenario == "S0") return@LaunchedEffect
        onIntent(ArDogIntent.ResetPerformanceScenario)
        if (scenario == PERFORMANCE_SCENARIO_TEARDOWN) return@LaunchedEffect
        while (currentState.readiness != CharacterReadiness.Ready) delay(50)
        when (scenario) {
            "S1" -> drivePlayerWorkload(onIntent, includeActions = false)
            "S2" -> {
                equipHeaviestVariant(onIntent)
                drivePlayerWorkload(onIntent, includeActions = true)
            }
            "S3" -> {
                onIntent(ArDogIntent.ActivateMultiModel)
                drivePlayerWorkload(onIntent, includeActions = true)
            }
            "S4" -> {
                equipHeaviestVariant(onIntent)
                onIntent(ArDogIntent.ActivateMultiModel)
                drivePlayerWorkload(onIntent, includeActions = true)
            }
            "S5", "S6" -> {
                val mission = if (scenario == "S5") MissionId.KillRoaches else MissionId.DefeatBobrito
                onIntent(ArDogIntent.SelectMission(mission))
                onIntent(ArDogIntent.StartMission)
                while (true) {
                    if (currentState.mission.phase != MissionPhase.Active) {
                        onIntent(ArDogIntent.ReplayMission)
                    }
                    onIntent(ArDogIntent.MoveBy(.04f, .025f))
                    onIntent(ArDogIntent.PlayAction(CharacterAction.Punch))
                    delay(700)
                }
            }
        }
    }
}

private suspend fun drivePlayerWorkload(
    onIntent: (ArDogIntent) -> Unit,
    includeActions: Boolean,
) {
    val actions = arrayOf(CharacterAction.Punch, CharacterAction.Dance, CharacterAction.Howl)
    var actionIndex = 0
    var direction = 1f
    var moving = true
    onIntent(ArDogIntent.MovementStarted)
    while (true) {
        if (moving) {
            onIntent(ArDogIntent.MoveBy(.025f * direction, .012f * direction))
            onIntent(ArDogIntent.RotateBy(4f * direction))
            onIntent(ArDogIntent.SetScale(if (direction > 0) 1.12f else .9f))
        }
        if (includeActions && actionIndex % 6 == 0) {
            onIntent(ArDogIntent.PlayAction(actions[(actionIndex / 6) % actions.size]))
        }
        actionIndex++
        if (actionIndex % 12 == 0) {
            moving = !moving
            onIntent(if (moving) ArDogIntent.MovementStarted else ArDogIntent.MovementStopped)
        }
        direction *= -1f
        delay(250)
    }
}

private fun equipHeaviestVariant(onIntent: (ArDogIntent) -> Unit) {
    onIntent(ArDogIntent.ToggleAccessory(AccessoryId.Pipe))
    onIntent(ArDogIntent.ToggleAccessory(AccessoryId.BirthdayGlasses))
}

@Composable
private fun ModelGestureLayer(onIntent: (ArDogIntent) -> Unit, modifier: Modifier) {
    Box(modifier.pointerInput(Unit) {
        detectDragGestures { change, drag ->
            change.consume()
            if (abs(drag.x) > abs(drag.y) * 1.6f) onIntent(ArDogIntent.RotateBy(drag.x * .45f))
            else onIntent(ArDogIntent.MoveBy(drag.x / size.width, -drag.y / size.height))
        }
    })
}

@Composable
private fun ActionControls(onIntent: (ArDogIntent) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ActionButton(R.drawable.accessory_birthday_glasses, R.string.open_accessories, PerformanceTestTags.OpenAccessories) { onIntent(ArDogIntent.ToggleSelector) }
        ActionButton(R.drawable.action_attack, R.string.action_punch, PerformanceTestTags.Punch) { onIntent(ArDogIntent.PlayAction(CharacterAction.Punch)) }
        ActionButton(R.drawable.action_dance, R.string.action_dance, PerformanceTestTags.Dance) { onIntent(ArDogIntent.PlayAction(CharacterAction.Dance)) }
        ActionButton(R.drawable.action_howl, R.string.action_howl, PerformanceTestTags.Howl) { onIntent(ArDogIntent.PlayAction(CharacterAction.Howl)) }
    }
}

@Composable
private fun ActionButton(icon: Int, label: Int, testTag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(Color(0xB33C3F44))
            .clickable(onClick = onClick).testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), stringResource(label), Modifier.size(48.dp))
    }
}
