package com.example.ardogdemo.audio

import android.content.Context
import com.example.ardogdemo.R
import com.example.ardogdemo.domain.mission.CombatSound

class CombatAudioPlayer(context: Context) {
    private val sounds = ShortSoundPool(
        context,
        mapOf(
            CombatSound.EnemyHit1 to R.raw.enemy_hit_1,
            CombatSound.EnemyHit2 to R.raw.enemy_hit_2,
            CombatSound.EnemyHit3 to R.raw.enemy_hit_3,
            CombatSound.EnemyDie to R.raw.enemy_die,
            CombatSound.Victory to R.raw.mission_complete,
            CombatSound.Defeat to R.raw.mission_failed,
        ),
    )

    fun play(sound: CombatSound) { sounds.play(sound) }

    fun release() = sounds.release()
}
