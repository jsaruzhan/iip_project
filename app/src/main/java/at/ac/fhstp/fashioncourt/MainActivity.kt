package at.ac.fhstp.fashioncourt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import at.ac.fhstp.fashioncourt.camera.CameraManager
import at.ac.fhstp.fashioncourt.detection.PoseDetectorHelper
import at.ac.fhstp.fashioncourt.overlay.CalibrationOverlayView
import at.ac.fhstp.fashioncourt.overlay.PoseOverlayView
import at.ac.fhstp.fashioncourt.ui.theme.FashionCourtTheme
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout

class MainActivity : ComponentActivity() {

    private var cameraManager: CameraManager? = null
    private var poseDetectorHelper: PoseDetectorHelper? = null
    private var poseOverlayView: PoseOverlayView? = null
    private var calibrationOverlayView: CalibrationOverlayView? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraAndDetection()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FashionCourtTheme {
                ARTryOnScreen(
                    onViewsCreated = { previewView, poseOverlay, calibrationOverlay, switchButton ->
                        poseOverlayView = poseOverlay
                        calibrationOverlayView = calibrationOverlay

                        calibrationOverlay.onCalibrationComplete = {
                            runOnUiThread {
                                calibrationOverlayView?.visibility = android.view.View.GONE
                                poseOverlayView?.visibility = android.view.View.VISIBLE
                                Toast.makeText(this, "Calibration complete!", Toast.LENGTH_SHORT).show()
                            }
                        }

                        setupCamera(previewView)

                        switchButton.setOnClickListener {
                            cameraManager?.switchCamera()
                        }

                        checkCameraPermission()
                    }
                )
            }
        }
    }

    private fun setupCamera(previewView: PreviewView) {
        cameraManager = CameraManager(
            context = this,
            previewView = previewView,
            onFrameAnalyzed = { bitmap, frameTime ->
                poseDetectorHelper?.detectAsync(bitmap, frameTime)
            }
        )

        poseDetectorHelper = PoseDetectorHelper(
            context = this,
            onResults = { result, _, _, _ ->
                runOnUiThread {
                    poseOverlayView?.updateResults(result)
                    calibrationOverlayView?.updateResults(result)
                }
            },
            onError = { error ->
                Log.e("MainActivity", "Pose detection error: $error")
            }
        )
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraAndDetection()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraAndDetection() {
        cameraManager?.startCamera(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.shutdown()
        poseDetectorHelper?.close()
    }
}

@Composable
fun ARTryOnScreen(
    onViewsCreated: (PreviewView, PoseOverlayView, CalibrationOverlayView, ImageButton) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                val previewView = PreviewView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val poseOverlay = PoseOverlayView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    visibility = android.view.View.GONE
                }

                val calibrationOverlay = CalibrationOverlayView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                val switchButton = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        150,
                        150
                    ).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 80, 40, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80F8BBD9")) // Semi-transparent pink
                    setImageResource(android.R.drawable.ic_menu_camera)
                    setPadding(20, 20, 20, 20)
                }

                addView(previewView)
                addView(poseOverlay)
                addView(calibrationOverlay)
                addView(switchButton)

                onViewsCreated(previewView, poseOverlay, calibrationOverlay, switchButton)
            }
        }
    )
}