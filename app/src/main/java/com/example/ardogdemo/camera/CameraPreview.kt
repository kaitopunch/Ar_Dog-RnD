package com.example.ardogdemo.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ardogdemo.diagnostics.PerformanceTestTags
import com.example.ardogdemo.diagnostics.RuntimeDiagnostics
import com.example.ardogdemo.diagnostics.RuntimeMetric

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val preview = remember(previewView) {
        Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.testTag(PerformanceTestTags.Camera),
    )

    DisposableEffect(lifecycleOwner, preview) {
        val future = ProcessCameraProvider.getInstance(applicationContext)
        val executor = ContextCompat.getMainExecutor(applicationContext)
        var disposed = false
        var bound = false
        var streamingRecorded = false
        val firstFrameTrace = RuntimeDiagnostics.beginAsyncTrace("ArDogCameraFirstFrame")
        val streamObserver = Observer<PreviewView.StreamState> { streamState ->
            if (streamState == PreviewView.StreamState.STREAMING && !streamingRecorded) {
                streamingRecorded = true
                RuntimeDiagnostics.mark(RuntimeMetric.CameraStreaming)
                RuntimeDiagnostics.endAsyncTrace("ArDogCameraFirstFrame", firstFrameTrace)
            }
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        preview.surfaceProvider = previewView.surfaceProvider
        RuntimeDiagnostics.mark(RuntimeMetric.CameraBindRequested)
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val provider = future.get()
                if (!provider.isBound(preview)) {
                    RuntimeDiagnostics.trace("ArDogCameraBind") {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                        )
                    }
                    bound = true
                    RuntimeDiagnostics.mark(RuntimeMetric.CameraBound)
                }
            }.onFailure {
                RuntimeDiagnostics.mark(RuntimeMetric.CameraError)
                RuntimeDiagnostics.endAsyncTrace("ArDogCameraFirstFrame", firstFrameTrace)
            }
        }, executor)
        onDispose {
            disposed = true
            previewView.previewStreamState.removeObserver(streamObserver)
            preview.surfaceProvider = null
            RuntimeDiagnostics.endAsyncTrace("ArDogCameraFirstFrame", firstFrameTrace)
            if (future.isDone && bound) {
                runCatching { future.get().unbind(preview) }
                    .onSuccess { RuntimeDiagnostics.mark(RuntimeMetric.CameraClosed) }
                    .onFailure { RuntimeDiagnostics.mark(RuntimeMetric.CameraError) }
            }
        }
    }
}
