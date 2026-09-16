package com.example.ardogdemo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

internal class ShortSoundPool<K : Any>(context: Context, private val resources: Map<K, Int>) {
    private val lock = Any()
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val applicationContext = context.applicationContext
    private val soundIds = mutableMapOf<K, Int>()
    private val soundKeys = mutableMapOf<Int, K>()
    private val loadedIds = mutableSetOf<Int>()
    private val pendingIds = mutableSetOf<Int>()
    private val activeStreams = mutableMapOf<K, Int>()
    private var released = false

    init {
        pool.setOnLoadCompleteListener { soundPool, soundId, status ->
            synchronized(lock) {
                if (released || status != 0) return@setOnLoadCompleteListener
                loadedIds += soundId
                if (pendingIds.remove(soundId)) {
                    soundKeys[soundId]?.let { key ->
                        activeStreams[key] = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
                    }
                }
            }
        }
    }

    fun preload(key: K) {
        synchronized(lock) { load(key) }
    }

    fun play(key: K) = synchronized(lock) {
        if (released) return@synchronized
        val soundId = load(key)
        if (soundId in loadedIds) {
            activeStreams[key] = pool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
        else {
            pendingIds += soundId
        }
    }

    fun stop(key: K) {
        synchronized(lock) {
            soundIds[key]?.let(pendingIds::remove)
            activeStreams.remove(key)?.let(pool::stop)
        }
    }

    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            pendingIds.clear()
            loadedIds.clear()
            soundIds.clear()
            soundKeys.clear()
            activeStreams.clear()
            pool.release()
        }
    }

    private fun load(key: K): Int = soundIds.getOrPut(key) {
        pool.load(applicationContext, resources.getValue(key), 1).also { soundKeys[it] = key }
    }
}
