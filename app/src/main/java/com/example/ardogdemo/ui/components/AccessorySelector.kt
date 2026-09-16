package com.example.ardogdemo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ardogdemo.R
import com.example.ardogdemo.domain.character.AccessoryId

@Composable
fun AccessorySelector(
    selected: Set<AccessoryId>,
    onSelect: (AccessoryId) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Color(0xD92B2D31)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("X", color = Color.White) }
        }
        AccessoryId.entries.forEach { id ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(id) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(painterResource(id.icon()), null, Modifier.size(54.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Text(stringResource(id.label()), color = Color.White, modifier = Modifier.weight(1f))
                if (id in selected) Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF35C96A)), contentAlignment = Alignment.Center) { Text("OK", color = Color.White) }
            }
        }
    }
}

private fun AccessoryId.icon() = when (this) {
    AccessoryId.Pipe -> R.drawable.accessory_pipe
    AccessoryId.Cigar -> R.drawable.accessory_cigar
    AccessoryId.BirthdayGlasses -> R.drawable.accessory_birthday_glasses
    AccessoryId.SpiralGlasses -> R.drawable.accessory_spiral_glasses
}
private fun AccessoryId.label() = when (this) {
    AccessoryId.Pipe -> R.string.accessory_pipe
    AccessoryId.Cigar -> R.string.accessory_cigar
    AccessoryId.BirthdayGlasses -> R.string.accessory_birthday_glasses
    AccessoryId.SpiralGlasses -> R.string.accessory_spiral_glasses
}
