package com.example.ardogdemo.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ardogdemo.R
import com.example.ardogdemo.domain.mission.EnemyKind
import com.example.ardogdemo.domain.mission.EnemyState
import com.example.ardogdemo.domain.mission.MissionId
import com.example.ardogdemo.domain.mission.MissionPhase
import com.example.ardogdemo.domain.mission.MissionState
import com.example.ardogdemo.domain.mission.PLAYER_MAX_HP
import com.example.ardogdemo.diagnostics.PerformanceTestTags

@Composable
fun MissionOverlay(state: MissionState, onIntent: (ArDogIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MissionStatus(state)
        if (state.phase == MissionPhase.Preview) {
            Row(
                Modifier.padding(top = 8.dp).background(Color(0xD927292D), RoundedCornerShape(8.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MissionChoice(
                    stringResource(R.string.mission_roaches_short),
                    state.selected == MissionId.KillRoaches,
                    PerformanceTestTags.mission(MissionId.KillRoaches.name),
                ) { onIntent(ArDogIntent.SelectMission(MissionId.KillRoaches)) }
                MissionChoice(
                    stringResource(R.string.mission_bobrito_short),
                    state.selected == MissionId.DefeatBobrito,
                    PerformanceTestTags.mission(MissionId.DefeatBobrito.name),
                ) { onIntent(ArDogIntent.SelectMission(MissionId.DefeatBobrito)) }
            }
            Button(
                onClick = { onIntent(ArDogIntent.StartMission) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF48D93D), contentColor = Color(0xFF24170E)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 8.dp).width(230.dp).height(54.dp)
                    .testTag(PerformanceTestTags.StartMission),
            ) {
                Text(stringResource(R.string.start_mission), fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MissionStatus(state: MissionState) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xD9F4E5CF)).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                if (state.selected == MissionId.KillRoaches) R.string.mission_roaches_title
                else R.string.mission_bobrito_title,
            ),
            color = Color(0xFF32120F),
            fontWeight = FontWeight.Black,
            fontSize = 21.sp,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${state.kills}/${state.definition.requiredKills}", color = Color(0xFF32120F), fontWeight = FontWeight.Bold)
            if (state.phase != MissionPhase.Preview) {
                Text(stringResource(R.string.mission_hp, state.playerHp, PLAYER_MAX_HP), color = Color(0xFF8A1717), fontWeight = FontWeight.Black)
            }
        }
        if (state.phase == MissionPhase.Active) {
            state.enemies.filter(EnemyState::alive).forEach { enemy ->
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (enemy.kind == EnemyKind.Bobrito) stringResource(R.string.mission_enemy_bobrito)
                        else stringResource(R.string.mission_enemy_roach, enemy.id + 1),
                        color = Color(0xFF32120F),
                        fontSize = 11.sp,
                        modifier = Modifier.width(70.dp),
                    )
                    Box(Modifier.weight(1f).height(7.dp).background(Color(0xFF5B2323), RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(enemy.hp.toFloat() / enemy.kind.maxHp).height(7.dp).background(Color(0xFFE33B35), RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionChoice(label: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color(0xFF211308) else Color.White,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(108.dp)
            .testTag(testTag)
            .background(if (selected) Color(0xFFFFCF43) else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) Color(0xFF8C5B10) else Color(0xFF73757B), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    )
}

@Composable
fun MissionResultOverlay(phase: MissionPhase, onIntent: (ArDogIntent) -> Unit, modifier: Modifier = Modifier) {
    if (phase != MissionPhase.Victory && phase != MissionPhase.Defeat) return
    Box(modifier.background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(290.dp).background(Color(0xFFF7E4C7), RoundedCornerShape(8.dp)).border(3.dp, Color(0xFF4B2517), RoundedCornerShape(8.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (phase == MissionPhase.Victory) R.string.mission_victory else R.string.mission_defeat),
                color = if (phase == MissionPhase.Victory) Color(0xFF25832B) else Color(0xFFA91F1B),
                fontWeight = FontWeight.Black,
                fontSize = 30.sp,
            )
            Button(
                onClick = { onIntent(ArDogIntent.ReplayMission) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF48D93D), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.mission_replay), fontWeight = FontWeight.Bold) }
            Button(
                onClick = { onIntent(ArDogIntent.ExitMission) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC43B), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.mission_exit), fontWeight = FontWeight.Bold) }
        }
    }
}
