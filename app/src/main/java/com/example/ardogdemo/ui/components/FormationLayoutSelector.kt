package com.example.ardogdemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ardogdemo.R
import com.example.ardogdemo.diagnostics.PerformanceTestTags
import com.example.ardogdemo.domain.character.FormationLayout

@Composable
fun FormationLayoutSelector(
    selected: FormationLayout,
    enabled: Boolean,
    onSelect: (FormationLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stacked under the Clone Jutsu tile so the chips stay clear of the mission HUD to their right.
    Column(
        modifier.width(IntrinsicSize.Max).alpha(if (enabled) 1f else .5f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FormationLayout.entries.forEach { layout ->
            val isSelected = layout == selected
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xCCFFC107) else Color(0xB33C3F44))
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(layout) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag(PerformanceTestTags.formation(layout.name.lowercase())),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(layout.label()),
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun FormationLayout.label(): Int = when (this) {
    FormationLayout.Row -> R.string.formation_row
    FormationLayout.Triangle -> R.string.formation_triangle
    FormationLayout.Circle -> R.string.formation_circle
}
