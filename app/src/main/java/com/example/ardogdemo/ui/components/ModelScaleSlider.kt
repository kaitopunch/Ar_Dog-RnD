package com.example.ardogdemo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ardogdemo.R
import kotlin.math.roundToInt

@Composable
fun ModelScaleSlider(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(48.dp).height(190.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.control_zoom_in), null, Modifier.size(26.dp))
        VerticalScaleTrack(value, enabled, onValueChange, Modifier.weight(1f))
        Image(painterResource(R.drawable.control_zoom_out), null, Modifier.size(26.dp))
    }
}

@Composable
private fun VerticalScaleTrack(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
) {
    var heightPx by remember { mutableIntStateOf(0) }
    val thumbRadiusPx = with(LocalDensity.current) { THUMB_RADIUS.roundToPx() }
    val fraction = ((value - MIN_SCALE) / (MAX_SCALE - MIN_SCALE)).coerceIn(0f, 1f)
    Box(
        modifier
            .width(44.dp)
            .onSizeChanged { heightPx = it.height }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, MIN_SCALE..MAX_SCALE)
            }
            .pointerInput(enabled, heightPx) {
                if (!enabled || heightPx == 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onValueChange(scaleForY(down.position.y, heightPx))
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            onValueChange(scaleForY(change.position.y, heightPx))
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Color.White.copy(alpha = .42f), CircleShape))
        Box(
            Modifier
                .offset {
                    val travelPx = (heightPx - thumbRadiusPx * 2).coerceAtLeast(0)
                    IntOffset(0, ((1f - fraction) * travelPx).roundToInt())
                }
                .size(22.dp)
                .background(if (enabled) Color.White else Color.Gray, CircleShape),
        )
    }
}

private fun scaleForY(y: Float, height: Int): Float {
    val fraction = 1f - (y / height).coerceIn(0f, 1f)
    return MIN_SCALE + fraction * (MAX_SCALE - MIN_SCALE)
}

private const val MIN_SCALE = .55f
private const val MAX_SCALE = 1.60f
private val THUMB_RADIUS: Dp = 11.dp
