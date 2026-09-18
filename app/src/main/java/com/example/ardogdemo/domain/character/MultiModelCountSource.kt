package com.example.ardogdemo.domain.character

/** Supplies the remote multi-model total count; null means the remote value is unavailable. */
fun interface MultiModelCountSource {
    suspend fun fetch(): Int?
}
