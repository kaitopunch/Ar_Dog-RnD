package com.example.ardogdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.ardogdemo.diagnostics.PERFORMANCE_SCENARIO_EXTRA
import com.example.ardogdemo.diagnostics.PerformanceScenarioController
import com.example.ardogdemo.presentation.ArDogRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PerformanceScenarioController.activate(intent.getStringExtra(PERFORMANCE_SCENARIO_EXTRA))
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface { ArDogRoute() }
            }
        }
    }
}
