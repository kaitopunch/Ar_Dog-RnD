package com.example.ardogdemo.audio

import android.content.Context
import com.example.ardogdemo.R

class CharacterAudioPlayer(context: Context) {
    private val sounds = ShortSoundPool(context, mapOf(HOWL to R.raw.gugu)).also { it.preload(HOWL) }

    fun playHowl() {
        stop()
        sounds.play(HOWL)
    }

    fun stop() = sounds.stop(HOWL)

    fun release() = sounds.release()
}

private const val HOWL = "howl"
