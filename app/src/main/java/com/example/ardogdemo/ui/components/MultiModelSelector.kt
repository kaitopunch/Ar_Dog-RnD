package com.example.ardogdemo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

@Composable
fun MultiModelSelector(
    icon: Painter,
    contentDescription: String,
    remainingMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = remainingMs > 0L
    val remainingFraction = (remainingMs.toFloat() / durationMs).coerceIn(0f, 1f)
    Box(
        modifier
            .size(52.dp)
            .clip(RectangleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(icon, contentDescription, Modifier.fillMaxSize())
        if (active) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0x99000000),
                    startAngle = -90f,
                    sweepAngle = 360f * (1f - remainingFraction),
                    useCenter = true,
                )
            }
            Text(
                text = "${ceil(remainingMs / 1_000.0).toInt()}s",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 5f)),
            )
        }
    }
}
