package at.ac.fhstp.fashioncourt.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val previewView: PreviewView,
    private val onFrameAnalyzed: (Bitmap, Long) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lifecycleOwner: LifecycleOwner? = null
    private var isFrontCamera = true  // Default to front camera

    fun startCamera(lifecycleOwner: LifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera() {
        val cameraProvider = cameraProvider ?: return
        val lifecycleOwner = lifecycleOwner ?: return

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetRotation(previewView.display.rotation)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeImage(imageProxy)
                }
            }

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e("CameraManager", "Use case binding failed", e)
        }
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        bindCamera()
    }

    fun isFrontCamera(): Boolean = isFrontCamera

    private fun analyzeImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        // Mirror for front camera
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        val frameTime = SystemClock.uptimeMillis()
        onFrameAnalyzed(rotatedBitmap, frameTime)

        imageProxy.close()
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}