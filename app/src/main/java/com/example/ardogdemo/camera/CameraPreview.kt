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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

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

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner, preview) {
        val future = ProcessCameraProvider.getInstance(applicationContext)
        val executor = ContextCompat.getMainExecutor(applicationContext)
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val provider = future.get()
            if (provider.isBound(preview)) return@addListener
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        }, executor)
        onDispose {
            disposed = true
            preview.surfaceProvider = null
            if (future.isDone) future.get().unbind(preview)
        }
    }
}
