package com.example.ardogdemo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ardogdemo.R
import com.example.ardogdemo.diagnostics.PerformanceTestTags
import kotlin.math.roundToInt

@Composable
fun MovementJoystick(
    enabled: Boolean,
    onMove: (Float, Float) -> Unit,
    onMoveStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var direction by remember { mutableStateOf(Offset.Zero) }
    var moving by remember { mutableStateOf(false) }
    val currentDirection by rememberUpdatedState(direction)
    LaunchedEffect(enabled) {
        if (!enabled && moving) {
            knob = Offset.Zero
            direction = Offset.Zero
            moving = false
            onMoveStateChanged(false)
        }
    }
    LaunchedEffect(enabled, moving) {
        if (!enabled || !moving) return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = ((frameNanos - previousFrameNanos) / NANOS_PER_SECOND)
                .coerceIn(0f, MAX_FRAME_DELTA_SECONDS)
            previousFrameNanos = frameNanos
            onMove(
                currentDirection.x * MOVEMENT_UNITS_PER_SECOND * deltaSeconds,
                -currentDirection.y * MOVEMENT_UNITS_PER_SECOND * deltaSeconds,
            )
        }
    }
    fun stopMoving() {
        knob = Offset.Zero
        direction = Offset.Zero
        if (moving) onMoveStateChanged(false)
        moving = false
    }
    Box(
        modifier.size(132.dp).clip(CircleShape).testTag(PerformanceTestTags.Joystick)
            .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectDragGestures(
                onDragStart = {
                    moving = true
                    onMoveStateChanged(true)
                },
                onDragEnd = ::stopMoving,
                onDragCancel = ::stopMoving,
            ) { change, amount ->
                change.consume()
                knob = (knob + amount).let { value ->
                    val limit = minOf(size.width, size.height) * .28f
                    Offset(value.x.coerceIn(-limit, limit), value.y.coerceIn(-limit, limit)).also {
                        direction = it / limit
                    }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.control_joystick), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color.White.copy(.72f)))
        Box(Modifier.offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }.size(50.dp).clip(CircleShape))
    }
}

private const val MOVEMENT_UNITS_PER_SECOND = .55f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_DELTA_SECONDS = .05f
