package at.ac.fhstp.fashioncourt

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import at.ac.fhstp.fashioncourt.overlay.ClothingOverlayView
import at.ac.fhstp.fashioncourt.overlay.PoseOverlayView
import at.ac.fhstp.fashioncourt.ui.theme.FashionCourtTheme
import android.graphics.Color
import android.view.Gravity

class MainActivity : ComponentActivity() {

    private var cameraManager: CameraManager? = null
    private var poseDetectorHelper: PoseDetectorHelper? = null
    private var poseOverlayView: PoseOverlayView? = null
    private var calibrationOverlayView: CalibrationOverlayView? = null
    private var clothingOverlayView: ClothingOverlayView? = null

    // Clothing lists
    private val topsList = mutableListOf<Bitmap>()
    private val bottomsList = mutableListOf<Bitmap>()
    private val shoesList = mutableListOf<Bitmap>()
    private var currentTopIndex = 0
    private var currentBottomIndex = 0
    private var currentShoesIndex = 0

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

        loadAllClothing()

        setContent {
            FashionCourtTheme {
                ARTryOnScreen(
                    onViewsCreated = { previewView, poseOverlay, calibrationOverlay, clothingOverlay,
                                       switchButton, prevTopBtn, nextTopBtn, prevBottomBtn, nextBottomBtn,
                                       prevShoesBtn, nextShoesBtn ->
                        poseOverlayView = poseOverlay
                        calibrationOverlayView = calibrationOverlay
                        clothingOverlayView = clothingOverlay

                        calibrationOverlay.onCalibrationComplete = {
                            runOnUiThread {
                                calibrationOverlayView?.visibility = android.view.View.GONE
                                clothingOverlayView?.visibility = android.view.View.VISIBLE

                                // Show first clothing items
                                if (topsList.isNotEmpty()) {
                                    clothingOverlayView?.setTopBitmap(topsList[currentTopIndex])
                                }
                                if (bottomsList.isNotEmpty()) {
                                    clothingOverlayView?.setBottomBitmap(bottomsList[currentBottomIndex])
                                }
                                if (shoesList.isNotEmpty()) {
                                    clothingOverlayView?.setShoesBitmap(shoesList[currentShoesIndex])
                                }

                                // Show all navigation buttons
                                prevTopBtn.visibility = android.view.View.VISIBLE
                                nextTopBtn.visibility = android.view.View.VISIBLE
                                prevBottomBtn.visibility = android.view.View.VISIBLE
                                nextBottomBtn.visibility = android.view.View.VISIBLE
                                prevShoesBtn.visibility = android.view.View.VISIBLE
                                nextShoesBtn.visibility = android.view.View.VISIBLE
                            }
                        }

                        switchButton.setOnClickListener {
                            cameraManager?.switchCamera()
                        }

                        // Top navigation
                        prevTopBtn.setOnClickListener {
                            if (topsList.isNotEmpty()) {
                                currentTopIndex = if (currentTopIndex > 0) currentTopIndex - 1 else topsList.size - 1
                                clothingOverlayView?.setTopBitmap(topsList[currentTopIndex])
                            }
                        }
                        nextTopBtn.setOnClickListener {
                            if (topsList.isNotEmpty()) {
                                currentTopIndex = (currentTopIndex + 1) % topsList.size
                                clothingOverlayView?.setTopBitmap(topsList[currentTopIndex])
                            }
                        }

                        // Bottom navigation
                        prevBottomBtn.setOnClickListener {
                            if (bottomsList.isNotEmpty()) {
                                currentBottomIndex = if (currentBottomIndex > 0) currentBottomIndex - 1 else bottomsList.size - 1
                                clothingOverlayView?.setBottomBitmap(bottomsList[currentBottomIndex])
                            }
                        }
                        nextBottomBtn.setOnClickListener {
                            if (bottomsList.isNotEmpty()) {
                                currentBottomIndex = (currentBottomIndex + 1) % bottomsList.size
                                clothingOverlayView?.setBottomBitmap(bottomsList[currentBottomIndex])
                            }
                        }

                        // Shoes navigation
                        prevShoesBtn.setOnClickListener {
                            if (shoesList.isNotEmpty()) {
                                currentShoesIndex = if (currentShoesIndex > 0) currentShoesIndex - 1 else shoesList.size - 1
                                clothingOverlayView?.setShoesBitmap(shoesList[currentShoesIndex])
                            }
                        }
                        nextShoesBtn.setOnClickListener {
                            if (shoesList.isNotEmpty()) {
                                currentShoesIndex = (currentShoesIndex + 1) % shoesList.size
                                clothingOverlayView?.setShoesBitmap(shoesList[currentShoesIndex])
                            }
                        }

                        setupCamera(previewView)
                        checkCameraPermission()
                    }
                )
            }
        }
    }

    private fun loadAllClothing() {
        // Load tops
        for (i in 1..5) {
            loadBitmapFromAssets("clothes/Tops/top$i.png")?.let { topsList.add(it) }
        }

        // Load bottoms (folder is "Bottom" not "Bottoms")
        for (i in 1..4) {
            loadBitmapFromAssets("clothes/Bottom/bottom$i.png")?.let { bottomsList.add(it) }
        }

        // Load shoes
        for (i in 1..4) {
            loadBitmapFromAssets("clothes/Shoes/shoes$i.png")?.let { shoesList.add(it) }
        }

        Log.d("MainActivity", "Loaded ${topsList.size} tops, ${bottomsList.size} bottoms, ${shoesList.size} shoes")
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
                    clothingOverlayView?.updateResults(result)
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

    private fun loadBitmapFromAssets(path: String): Bitmap? {
        return try {
            assets.open(path).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load bitmap: $path", e)
            null
        }
    }
}

@Composable
fun ARTryOnScreen(
    onViewsCreated: (PreviewView, PoseOverlayView, CalibrationOverlayView, ClothingOverlayView,
                     ImageButton, ImageButton, ImageButton, ImageButton, ImageButton,
                     ImageButton, ImageButton) -> Unit
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

                val clothingOverlay = ClothingOverlayView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    visibility = android.view.View.GONE
                }

                val switchButton = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(120, 120).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 80, 40, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80F8BBD9"))
                    setImageResource(android.R.drawable.ic_menu_camera)
                    setPadding(20, 20, 20, 20)
                }

                // Top buttons - at shoulder/chest level (25% from top)
                val prevTopBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 400, 0, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80F8BBD9"))
                    setImageResource(android.R.drawable.ic_media_previous)
                    visibility = android.view.View.GONE
                }

                val nextTopBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 400, 20, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80F8BBD9"))
                    setImageResource(android.R.drawable.ic_media_next)
                    visibility = android.view.View.GONE
                }

                // Bottom/pants buttons - at hip/thigh level (50% from top)
                val prevBottomBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 900, 0, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80E1BEE7"))
                    setImageResource(android.R.drawable.ic_media_previous)
                    visibility = android.view.View.GONE
                }

                val nextBottomBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 900, 20, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80E1BEE7"))
                    setImageResource(android.R.drawable.ic_media_next)
                    visibility = android.view.View.GONE
                }

                // Shoes buttons - at feet level (80% from top)
                val prevShoesBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.START
                        setMargins(20, 1500, 0, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80B3E5FC"))
                    setImageResource(android.R.drawable.ic_media_previous)
                    visibility = android.view.View.GONE
                }

                val nextShoesBtn = ImageButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, 1500, 20, 0)
                    }
                    setBackgroundColor(Color.parseColor("#80B3E5FC"))
                    setImageResource(android.R.drawable.ic_media_next)
                    visibility = android.view.View.GONE
                }

                addView(previewView)
                addView(poseOverlay)
                addView(clothingOverlay)
                addView(calibrationOverlay)
                addView(switchButton)
                addView(prevTopBtn)
                addView(nextTopBtn)
                addView(prevBottomBtn)
                addView(nextBottomBtn)
                addView(prevShoesBtn)
                addView(nextShoesBtn)

                onViewsCreated(previewView, poseOverlay, calibrationOverlay, clothingOverlay,
                    switchButton, prevTopBtn, nextTopBtn, prevBottomBtn, nextBottomBtn,
                    prevShoesBtn, nextShoesBtn)
            }
        }
    )
}