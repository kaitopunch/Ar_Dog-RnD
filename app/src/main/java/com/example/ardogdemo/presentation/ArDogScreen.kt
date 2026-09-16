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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.example.ardogdemo.domain.character.CharacterAction
import com.example.ardogdemo.domain.character.CharacterReadiness
import com.example.ardogdemo.domain.mission.CombatSound
import com.example.ardogdemo.domain.mission.MissionPhase
import com.example.ardogdemo.scene.ModelScene
import com.example.ardogdemo.ui.components.AccessorySelector
import com.example.ardogdemo.ui.components.ModelScaleSlider
import com.example.ardogdemo.ui.components.MultiModelSelector
import com.example.ardogdemo.ui.components.MovementJoystick
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun ArDogRoute(viewModel: ArDogViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArDogScreen(state, viewModel::onIntent)
}

@Composable
fun ArDogScreen(state: ArDogState, onIntent: (ArDogIntent) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant_camera)) }
        }
        return
    }
    val audio = remember { CharacterAudioPlayer(context.applicationContext) }
    val combatAudio = remember { CombatAudioPlayer(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { audio.release(); combatAudio.release() } }
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
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(Modifier.fillMaxSize())
        ModelScene(state, { onIntent(ArDogIntent.CharacterReady) }, Modifier.fillMaxSize())
        MissionOverlay(
            state.mission,
            onIntent,
            Modifier.align(Alignment.TopCenter).padding(top = 22.dp).fillMaxSize(.92f),
        )
        MultiModelSelector(
            icon = painterResource(R.drawable.clone_jutsu),
            contentDescription = stringResource(R.string.activate_multi_model),
            remainingMs = state.multiModelRemainingMs,
            durationMs = MULTI_MODEL_DURATION_MS,
            enabled = state.readiness == CharacterReadiness.Ready,
            onClick = { onIntent(ArDogIntent.ActivateMultiModel) },
            modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 72.dp),
        )
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
    }
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
        ActionButton(R.drawable.accessory_birthday_glasses, R.string.open_accessories) { onIntent(ArDogIntent.ToggleSelector) }
        ActionButton(R.drawable.action_attack, R.string.action_punch) { onIntent(ArDogIntent.PlayAction(CharacterAction.Punch)) }
        ActionButton(R.drawable.action_dance, R.string.action_dance) { onIntent(ArDogIntent.PlayAction(CharacterAction.Dance)) }
        ActionButton(R.drawable.action_howl, R.string.action_howl) { onIntent(ArDogIntent.PlayAction(CharacterAction.Howl)) }
    }
}

@Composable
private fun ActionButton(icon: Int, label: Int, onClick: () -> Unit) {
    Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xB33C3F44)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(icon), stringResource(label), Modifier.size(48.dp))
    }
}
