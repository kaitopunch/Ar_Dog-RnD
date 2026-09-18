package com.example.ardogdemo.config

import android.util.Log
import com.example.ardogdemo.BuildConfig
import com.example.ardogdemo.domain.character.MultiModelCountSource
import com.example.ardogdemo.domain.character.MultiModelFormation
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Reads `multi_model_count` from Firebase Remote Config. Any failure, including a missing
 * Firebase configuration on builds without google-services.json, yields null so callers keep
 * the in-app default.
 */
class FirebaseMultiModelCountSource(
    private val remoteConfigProvider: () -> FirebaseRemoteConfig = { Firebase.remoteConfig },
) : MultiModelCountSource {
    override suspend fun fetch(): Int? = try {
        val remoteConfig = remoteConfigProvider()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else RELEASE_FETCH_INTERVAL_SECONDS
            },
        ).await()
        remoteConfig.setDefaultsAsync(mapOf(KEY to MultiModelFormation.DEFAULT_COUNT.toLong())).await()
        remoteConfig.fetchAndActivate().await()
        remoteConfig.getLong(KEY)
            .coerceIn(MultiModelFormation.MIN_COUNT.toLong(), MultiModelFormation.MAX_COUNT.toLong())
            .toInt()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Remote multi-model count unavailable", e)
        null
    }

    private companion object {
        const val TAG = "MultiModelCount"
        const val KEY = "multi_model_count"
        const val RELEASE_FETCH_INTERVAL_SECONDS = 3_600L
    }
}
